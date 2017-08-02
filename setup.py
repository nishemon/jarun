#!/usr/bin/env python
# -*- coding:utf-8 -*-
from __future__ import absolute_import
from __future__ import unicode_literals
import os

from setuptools import setup, find_packages

SRC = 'marun'

try:
	with open('README.md') as f:
		readme = f.read()
except IOError:
	readme = ''

def _requires_from_file(filename):
	return open(filename).read().splitlines()

# version
here = os.path.dirname(os.path.abspath(__file__))
version = next(
	(
		line.split('=', 2)[1].strip(" \t'")
		  for line in open(os.path.join(here, SRC, '__init__.py'))
		  if line.startswith('__version__ = ')
	), '0.0.2'
)

setup(
    name="marun",
    version=version,
    url='https://github.com/nishemon/marun',
    author='S.Takai',
    author_email='shtk@cccis.jp',
    maintainer='S.Takai',
    maintainer_email='shtk@cccis.jp',
    description='get an artifact and dependencies (java jar files) from maven repositories, like pip in python.',
    long_description=readme,
    packages=find_packages(),
    install_requires=_requires_from_file('requirements.txt'),
    license="MIT",
    classifiers=[
        'Development Status :: 2 - Pre-Alpha',
        'Environment :: Console',
        'Operating System :: POSIX',
        'Programming Language :: Java',
        'Programming Language :: Python :: 2.7',
        'License :: OSI Approved :: MIT License',
    ],
    python_requires = '>=2.7, <3',
    entry_points={
        'console_scripts': [
            'marun=marun.marun:main',
        ],
    },
    include_package_data=True,
    data_files=[('/etc', ['marun.conf.example'])],
    keywords=['java', 'maven', 'deploy', 'ivy', 'repository', 'package', 'artifact', 'run'],
)

