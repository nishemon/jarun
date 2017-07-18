import argparse
import imp

import Java


def load_flavor(name, flavorpaths):
	try:
		f,n,d = imp.find_module(name, flavorpaths)
		return imp.load_module(name, f, n, d)
	except ImportError:
		return None

def run(conf, args):
        java = Java.Java(conf)
        java.run(args)

def setup_subcmd(subparsers):
	run_parser = subparsers.add_parser('run')
	run_parser.add_argument('javaarg', nargs=argparse.REMAINDER)
	run_parser.set_defaults(handler=run)

