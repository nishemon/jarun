#!/usr/bin/env python
# -*- coding:utf-8 -*-
from __future__ import absolute_import
from __future__ import unicode_literals
import os

from setuptools import setup, find_packages

SRC = 'python'

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
	), '0.0.1'
)

setup(
    name="marun",
    version=version,
    url='https://github.com/nishemon/marun',
    author='S.Takai',
    author_email='shtk@cccis.jp',
    maintainer='S.Takai',
    maintainer_email='shtk@cccis.jp',
    description='Maven Artifact RUNner. Get jar files from maven repository and run it.',
    long_description=readme,
    packages=find_packages(),
    install_requires=_requires_from_file('requirements.txt'),
    license="MIT",
    classifiers=[
        'Programming Language :: Java'
        'License :: OSI Approved :: MIT License',
    ],
    entry_points="""
      # -*- Entry points: -*-
      [console_scripts]
      marun = python.marun:main
    """,
)

