/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011, 2012 Peter Güttinger
 * 
 */

package ch.njol.skript.registrations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Converter;
import ch.njol.skript.classes.Converter.ConverterInfo;
import ch.njol.skript.classes.Converter.ConverterUtils;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.DefaultExpression;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.log.SimpleLog;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.util.StringMode;
import ch.njol.skript.util.Utils;
import ch.njol.util.iterator.ArrayIterator;

/**
 * @author Peter Güttinger
 * 
 */
public abstract class Classes {
	
	private Classes() {}
	
	private static ClassInfo<?>[] classInfos = null;
	private final static List<ClassInfo<?>> tempClassInfos = new ArrayList<ClassInfo<?>>();
	private final static HashMap<Class<?>, ClassInfo<?>> exactClassInfos = new HashMap<Class<?>, ClassInfo<?>>();
	private final static HashMap<Class<?>, ClassInfo<?>> superClassInfos = new HashMap<Class<?>, ClassInfo<?>>();
	private final static HashMap<String, ClassInfo<?>> classInfosByCodeName = new HashMap<String, ClassInfo<?>>();
	
	/**
	 * @param info info about the class to register
	 */
	public static <T> void registerClass(final ClassInfo<T> info) {
		Skript.checkAcceptRegistrations();
		if (classInfosByCodeName.containsKey(info.getCodeName()))
			throw new IllegalArgumentException("Can't register " + info.getC().getName() + " with the code name " + info.getCodeName() + " because that name is already used by " + classInfosByCodeName.get(info.getCodeName()));
		if (exactClassInfos.containsKey(info.getC()))
			throw new IllegalArgumentException("Can't register the class info " + info.getCodeName() + " because the class " + info.getC().getName() + " is already registered");
		exactClassInfos.put(info.getC(), info);
		classInfosByCodeName.put(info.getCodeName(), info);
		tempClassInfos.add(info);
	}
	
	public final static void sortClassInfos() {
		assert classInfos == null;
		
		final LinkedList<ClassInfo<?>> classInfos = new LinkedList<ClassInfo<?>>();
		
		// merge before, after & sub/supertypes in before
		for (final ClassInfo<?> ci : tempClassInfos) {
			if (ci.after() != null && !ci.after().isEmpty()) {
				for (final ClassInfo<?> ci2 : tempClassInfos) {
					if (ci.after().contains(ci2.getCodeName())) {
						ci2.before().add(ci.getCodeName());
						ci.after().remove(ci2.getCodeName());
						if (ci.after().isEmpty())
							break;
					}
				}
			}
		}
		for (final ClassInfo<?> ci : tempClassInfos) {
			for (final ClassInfo<?> ci2 : tempClassInfos) {
				if (ci == ci2)
					continue;
				if (ci2.getC().isAssignableFrom(ci.getC()))
					ci.before().add(ci2.getCodeName());
			}
		}
		
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int i = 0; i < tempClassInfos.size(); i++) {
				final ClassInfo<?> ci = tempClassInfos.get(i);
				if (ci.before().isEmpty()) {
					classInfos.addFirst(ci);
					tempClassInfos.remove(i);
					i--;
					for (final ClassInfo<?> ci2 : tempClassInfos)
						ci2.before().remove(ci.getCodeName());
					changed = true;
				}
			}
		}
		Classes.classInfos = classInfos.toArray(new ClassInfo[classInfos.size()]);
		if (!tempClassInfos.isEmpty())
			throw new IllegalStateException("ClassInfos with circular dependencies detected: " + tempClassInfos);
		if (Skript.debug()) {
			for (final ClassInfo<?> ci : classInfos) {
				if (ci.before() != null && !ci.before().isEmpty() || ci.after() != null && !ci.after().isEmpty()) {
					final Set<String> s = new HashSet<String>();
					if (ci.before() != null)
						s.addAll(ci.before());
					if (ci.after() != null)
						s.addAll(ci.after());
					Skript.info(s.size() + " dependency/ies could not be resolved for " + ci + ": " + s);
				}
			}
		}
	}
	
	private final static void checkAllowClassInfoInteraction() {
		if (Skript.acceptRegistrations)
			throw new IllegalStateException("Cannot use classinfos until registration is over");
	}
	
	public static Iterable<ClassInfo<?>> getClassInfos() {
		checkAllowClassInfoInteraction();
		return new Iterable<ClassInfo<?>>() {
			@Override
			public Iterator<ClassInfo<?>> iterator() {
				return new ArrayIterator<ClassInfo<?>>(classInfos);
			}
		};
	}
	
	/**
	 * 
	 * @param codeName
	 * @return
	 * @throws SkriptAPIException If the given class was not registered
	 */
	public static ClassInfo<?> getClassInfo(final String codeName) {
		checkAllowClassInfoInteraction();
		final ClassInfo<?> ci = classInfosByCodeName.get(codeName);
		if (ci == null)
			throw new SkriptAPIException("no class info found for " + codeName);
		return ci;
	}
	
	public static ClassInfo<?> getClassInfoNoError(final String codeName) {
		checkAllowClassInfoInteraction();
		return classInfosByCodeName.get(codeName);
	}
	
	/**
	 * Gets the class info for the given class
	 * 
	 * @param c The exact class to get the class info for
	 * @return The class info for the given class of null if no info was found.
	 */
	public static <T> ClassInfo<T> getExactClassInfo(final Class<T> c) {
		checkAllowClassInfoInteraction();
		return (ClassInfo<T>) exactClassInfos.get(c);
	}
	
	/**
	 * Gets the class info of the given class or it's closest registered superclass. This method will never return null.
	 * 
	 * @param c
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> ClassInfo<? super T> getSuperClassInfo(final Class<T> c) {
		checkAllowClassInfoInteraction();
		final ClassInfo<?> i = superClassInfos.get(c);
		if (i != null)
			return (ClassInfo<? super T>) i;
		for (final ClassInfo<?> ci : classInfos) {
			if (ci.getC().isAssignableFrom(c)) {
				if (!Skript.acceptRegistrations)
					superClassInfos.put(c, ci);
				return (ClassInfo<? super T>) ci;
			}
		}
		return null;
	}
	
	/**
	 * Gets a class by it's code name
	 * 
	 * @param codeName
	 * @return the class
	 * @throws SkriptAPIException If the given class was not registered
	 */
	public static Class<?> getClass(final String codeName) {
		checkAllowClassInfoInteraction();
		return getClassInfo(codeName).getC();
	}
	
	/**
	 * As the name implies
	 * 
	 * @param name
	 * @return the class info or null if the name was not recognized
	 */
	public static ClassInfo<?> getClassInfoFromUserInput(String name) {
		checkAllowClassInfoInteraction();
		name = name.toLowerCase();
		for (final ClassInfo<?> ci : classInfos) {
			if (ci.getUserInputPatterns() == null)
				continue;
			for (final Pattern pattern : ci.getUserInputPatterns()) {
				if (pattern.matcher(name).matches())
					return ci;
			}
		}
		return null;
	}
	
	/**
	 * As the name implies
	 * 
	 * @param name
	 * @return the class or null if the name was not recognized
	 */
	public static Class<?> getClassFromUserInput(final String name) {
		checkAllowClassInfoInteraction();
		final ClassInfo<?> ci = getClassInfoFromUserInput(name);
		return ci == null ? null : ci.getC();
	}
	
	/**
	 * Gets a class by it's name (not code name)
	 * 
	 * @param name
	 * @return the class or null if the name was not recognized
	 */
	public static Class<?> getClassByName(final String name) {
		checkAllowClassInfoInteraction();
		for (final ClassInfo<?> ci : classInfos) {
			if (ci.getName().equalsIgnoreCase(name))
				return ci.getC();
		}
		return null;
	}
	
	/**
	 * Gets the default of a class
	 * 
	 * @param codeName
	 * @return the expression holding the default value or null if this class doesn't have one
	 * @throws SkriptAPIException If the given class was not registered
	 */
	public static DefaultExpression<?> getDefaultExpression(final String codeName) {
		checkAllowClassInfoInteraction();
		return getClassInfo(codeName).getDefaultExpression();
	}
	
	/**
	 * gets the default of a class
	 * 
	 * @param codeName
	 * @return the expression holding the default value or null if this class doesn't have one
	 */
	public static <T> DefaultExpression<T> getDefaultExpression(final Class<T> c) {
		checkAllowClassInfoInteraction();
		final ClassInfo<T> ci = (ClassInfo<T>) exactClassInfos.get(c);
		return ci == null ? null : ci.getDefaultExpression();
	}
	
	/**
	 * Gets the name a class was registered with.
	 * 
	 * @param c The exact class
	 * @return The name of the class or null if the given class wasn't registered.
	 */
	public final static String getExactClassName(final Class<?> c) {
		checkAllowClassInfoInteraction();
		final ClassInfo<?> ci = exactClassInfos.get(c);
		return ci == null ? null : ci.getCodeName();
	}
	
	/**
	 * parses without trying to convert anything.<br>
	 * Can log something if it doesn't return null.
	 * 
	 * @param s
	 * @param c
	 * @return
	 */
	public static <T> T parseSimple(final String s, final Class<T> c, final ParseContext context) {
		final SimpleLog log = SkriptLogger.startSubLog();
		for (final ClassInfo<?> info : classInfos) {
			if (info.getParser() == null || !c.isAssignableFrom(info.getC()))
				continue;
			log.clear();
			final T t = (T) info.getParser().parse(s, context);
			if (t != null) {
				SkriptLogger.stopSubLog(log);
				log.printLog();
				return t;
			}
		}
		SkriptLogger.stopSubLog(log);
		return null;
	}
	
	/**
	 * Parses a string to get an object of the desired type.<br/>
	 * Instead of repeatedly calling this with the same class argument, you should get a parser with {@link Classes#getParser(Class)} and use it for parsing.<br>
	 * Can log something if it doesn't return null.
	 * 
	 * @param s The string to parse
	 * @param c The desired type. The returned value will be of this type or a subclass if it.
	 * @return The parsed object.
	 */
	public static <T> T parse(final String s, final Class<T> c, final ParseContext context) {
		T t = parseSimple(s, c, context);
		if (t != null)
			return t;
		final SimpleLog log = SkriptLogger.startSubLog();
		for (final ConverterInfo<?, ?> conv : Converters.getConverters()) {
			if (c.isAssignableFrom(conv.to)) {
				log.clear();
				final Object o = parseSimple(s, conv.from, context);
				if (o != null) {
					t = (T) ConverterUtils.convert(conv, o);
					if (t != null) {
						SkriptLogger.stopSubLog(log);
						log.printLog();
						return t;
					}
				}
			}
		}
		SkriptLogger.stopSubLog(log);
		return null;
	}
	
	/**
	 * Gets a parser for parsing instances of the desired type from strings. The returned parser may only be used for parsing, i.e. you must not use its toString methods.
	 * 
	 * @param to
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static final <T> Parser<? extends T> getParser(final Class<T> to) {
		checkAllowClassInfoInteraction();
		for (int i = classInfos.length - 1; i >= 0; i--) {
			final ClassInfo<?> ci = classInfos[i];
			if (to.isAssignableFrom(ci.getC()) && ci.getParser() != null)
				return (Parser<? extends T>) ci.getParser();
		}
		for (final ConverterInfo<?, ?> conv : Converters.getConverters()) {
			if (to.isAssignableFrom(conv.to)) {
				for (int i = classInfos.length - 1; i >= 0; i--) {
					final ClassInfo<?> ci = classInfos[i];
					if (conv.from.isAssignableFrom(ci.getC()) && ci.getParser() != null)
						return Classes.createConvertedParser(ci.getParser(), (Converter<?, ? extends T>) conv.converter);
				}
			}
		}
		return null;
	}
	
	/**
	 * Gets a parser for an exactly known class. You should usually use {@link getParser} instead of this method.
	 * <p>
	 * The main benefit of this method is that it's the only class info method of Skript that can be used while Skript is initializing and thus useful for parsing configs.
	 * 
	 * @param c
	 * @return
	 */
	public static final <T> Parser<? extends T> getExactParser(final Class<T> c) {
		if (Skript.acceptRegistrations) {
			for (final ClassInfo<?> ci : tempClassInfos) {
				if (ci.getC() == c)
					return (Parser<? extends T>) ci.getParser();
			}
			return null;
		} else {
			final ClassInfo<T> ci = getExactClassInfo(c);
			return ci == null ? null : ci.getParser();
		}
	}
	
	private final static <F, T> Parser<T> createConvertedParser(final Parser<?> parser, final Converter<F, T> converter) {
		return new Parser<T>() {
			@SuppressWarnings("unchecked")
			@Override
			public T parse(final String s, final ParseContext context) {
				final Object f = parser.parse(s, context);
				if (f == null)
					return null;
				return converter.convert((F) f);
			}
			
			@Override
			public String toString(final T o) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public String toVariableNameString(final T o) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public String getVariableNamePattern() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	/**
	 * @param o Any object, preferably not an array: use {@link Classes#toString(Object[], boolean)} instead.
	 * @return String representation of the object (using a parser if found or {@link String#valueOf(Object)} otherwise).
	 * @see Classes#toString(Object, boolean)
	 * @see Parser
	 */
	public static String toString(final Object o) {
		return Classes.toString(o, StringMode.MESSAGE, false);
	}
	
	public static String getDebugMessage(final Object o) {
		return Classes.toString(o, StringMode.DEBUG, false);
	}
	
	public static final String toString(final Object[] os, final boolean and) {
		return Classes.toString(os, and, StringMode.MESSAGE, false);
	}
	
	public static final <T> String toString(final T o, final StringMode mode, final boolean plural) {
		final boolean code = mode == StringMode.VARIABLE_NAME;
		if (o == null)
			return "<none>";
		if (o.getClass().isArray()) {
			if (((Object[]) o).length == 0)
				return "<none>";
			final StringBuilder b = new StringBuilder();
			boolean first = true;
			for (final Object i : (Object[]) o) {
				if (!first)
					b.append(", ");
				b.append(toString(i, mode, plural));
				first = false;
			}
			return "[" + b.toString() + "]";
		}
		for (final ClassInfo<?> ci : classInfos) {
			if (ci.getParser() != null && ci.getC().isAssignableFrom(o.getClass())) {
				final String s = code ? ((Parser<T>) ci.getParser()).toVariableNameString(o) : Utils.toPlural(((Parser<T>) ci.getParser()).toString(o, mode), plural);
				if (s != null)
					return s;
			}
		}
		return code ? "object:" + o : String.valueOf(o);
	}
	
	public static final String toString(final Object[] os, final boolean and, final StringMode mode, final boolean plural) {
		if (os.length == 0)
			return toString(null);
		if (os.length == 1)
			return toString(os[0], mode, plural);
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < os.length; i++) {
			if (i != 0) {
				if (i == os.length - 1)
					b.append(and ? " and " : " or ");
				else
					b.append(", ");
			}
			b.append(toString(os[i], mode, plural));
		}
		return b.toString();
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public final static String[] serialize(final Object o) {
		final ClassInfo<?> ci = getSuperClassInfo(o.getClass());
		if (ci == null)
			return null;
		if (ci.getSerializeAs() != null) {
			final ClassInfo<?> as = getExactClassInfo(ci.getSerializeAs());
			if (as == null || as.getSerializer() == null)
				throw new SkriptAPIException(ci.getSerializeAs().getName() + ", the class to serialize " + o.getClass().getName() + " as, is not registered or not serializable");
			final Object s = Converters.convert(o, as.getC());
			if (s == null)
				return null;
			return new String[] {as.getCodeName(), ((Serializer) as.getSerializer()).serialize(s)};
		} else if (ci.getSerializer() != null) {
			return new String[] {ci.getCodeName(), ((Serializer) ci.getSerializer()).serialize(o)};
		}
		return null;
	}
	
	public final static Object deserialize(final String type, final String value) {
		final ClassInfo<?> ci = getClassInfo(type);
		if (ci == null || ci.getSerializer() == null)
			return null;
		return ci.getSerializer().deserialize(value);
	}
	
}