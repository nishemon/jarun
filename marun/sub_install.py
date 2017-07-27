
import json
import os

from logging import getLogger

import Consts
import App
import util

logger = getLogger(__name__)

def _add_new(builder, moduleId, new, curdeps, cur):
	if moduleId in curdeps:
		entry = curdeps[moduleId]
		if entry['revision'] == new['revision']:
			jar = cur.get_jar_path(entry['name'])
			if os.path.exists(jar):
				builder.add(moduleId, jar, entry, hard=True)
				return False
		else:
			logger.info("Update '%s' revision %s to %s", moduleId, entry['revision'], new['revision'])
	newjar = new['path']
	builder.add(moduleId, newjar, {
		'cache': newjar,
		'revision': new['revision']
	})
	return True

def _setup(conf, app, artifacts, keepold=False):
	args = ['Setup', 'runtime']
	args.extend(artifacts)
	sysjava = util.new_sys_java(conf)
	(code, output) = sysjava.sysRun(args, conf.toDict())
	if code != 0:
		return False
	builder = app.new_context_builder(artifacts)
	try:
		current = app.get_current_context()
		deps = output['resolve']['dependencies']
		curdeps = current.get_dependency_dict() if current else {}
		update = False
	        for d in deps:
			moduleId = d['id']
			update = update | _add_new(builder, moduleId, d, curdeps, current)
		if not update:
			builder.revert()
			return False
		outcontrols = current.get_uncontrol_jars() if current else []
		if len(outcontrols):
			logger.warn("There are out of control jars: %s", ",".join(outcontrols))
			for x in outcontrols:
				builder.add(x, None, hard=True)
		_check_errors(output)
	except:
		builder.revert()
		raise
	builder.commit(output['mains'], output['resources'], keepold=keepold)
	return True

def _check_errors(output):
	failures = output.get('failures', {})
	if failures:
		for undef, errors in failures.iteritems():
			classes = [x for x in errors if x.find('#') < 0]
			mains = [x for x in errors if 0 <= x.find('#main') ]
			print ("WARN: %s is undefine, so invalidate the below." % undef)
			if classes:
				print '\n'.join(["class: %s" % x for x in classes])
			if mains:
				print '\n'.join(["main(): %s" % x for x in mains])
	duplicates = output.get('duplicates', {})
	if duplicates:
		for path, jars in duplicates.iteritems():
			print ("WARN: resource %s is a duplicated entry." % path)
			print '\n'.join(["jar: %s" % x for x in jars])

def install(conf, args):
	app = App.AppRepository(conf)
	installed = app.get_current_context()
	# todo change to update if the same exists
	if not args.add and installed:
		return (False, 'already installed directory. Use "-a" for add jar file if you want.')
	if _setup(conf, app, args.artifact):
		return (True, None)
	return (False, None)

def update(conf, args):
	app = App.AppRepository(conf)
	installed = app.get_current_context()
	if not installed:
		return (False, 'not found "%s": no marun installed status.' % Consts.APP_STATUS_FILE)
	if _setup(conf, app, installed.get_installs(), args.keepold):
		return (True, None)
	return (False, None)

def setup_subcmd(subparsers):
	install_parser = subparsers.add_parser('install', help='Download jars.')
	install_parser.add_argument('artifact', nargs='+')
	install_parser.add_argument('-a', '--add', help='additional install', action='store_true')
	install_parser.add_argument('-d', '--libdir')
	install_parser.set_defaults(handler=install)
	
	update_parser = subparsers.add_parser('update', help='Not yet implemented.')
	update_parser.add_argument('-k', '--keepold', help='keep old install')
	#update_parser.add_argument('--minor', help='update minor version if pom.xml accept (default patch)')
	#update_parser.add_argument('--ignore-pom')
	update_parser.set_defaults(handler=update)


