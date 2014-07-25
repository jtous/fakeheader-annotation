/**
 * Copyright (C) 2012 Schneider Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Julien TOUS
 * Contributors: Stephane Seyvoz
 */

package org.ow2.mind.adl.annotations;

import java.io.File;
import java.util.Map;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Node;
import org.ow2.mind.adl.annotation.ADLLoaderPhase;
import org.ow2.mind.adl.annotation.AbstractADLLoaderAnnotationProcessor;
import org.ow2.mind.annotation.Annotation;
import org.ow2.mind.io.BasicOutputFileLocator;
import org.ow2.mind.adl.annotations.FakeHeaderGenerator;

import com.google.inject.Inject;
import com.google.inject.Injector;


/**
 * @author Julien TOUS
 */
public class FakeHeaderAnnotationProcessor extends
AbstractADLLoaderAnnotationProcessor {

	/*
	 * Works because our Loader is itself loaded by Google Guice.
	 */
	@Inject
	protected Injector injector;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.ow2.mind.adl.annotation.ADLLoaderAnnotationProcessor#processAnnotation
	 * (org.ow2.mind.annotation.Annotation, org.objectweb.fractal.adl.Node,
	 * org.objectweb.fractal.adl.Definition,
	 * org.ow2.mind.adl.annotation.ADLLoaderPhase, java.util.Map)
	 */
	public Definition processAnnotation(final Annotation annotation,
			final Node node, final Definition definition,
			final ADLLoaderPhase phase, final Map<Object, Object> context)
					throws ADLException {
		assert annotation instanceof FakeHeader;

				// Get instance from the injector so its @Inject fields get properly injected (ADL Loader especially)
		FakeHeaderGenerator headerWriter = injector.getInstance(FakeHeaderGenerator.class);
		headerWriter.writeComponentHeaders(definition, context);

		return null;
	}

}
