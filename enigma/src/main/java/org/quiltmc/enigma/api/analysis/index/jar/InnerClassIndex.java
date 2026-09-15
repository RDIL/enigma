package org.quiltmc.enigma.api.analysis.index.jar;

import org.jspecify.annotations.NonNull;
import org.quiltmc.enigma.api.translation.representation.entry.ClassDefEntry;
import org.quiltmc.enigma.api.translation.representation.entry.ClassEntry;

import java.util.HashSet;
import java.util.Set;

/**
 * An index of the classes a jar declares nested with the {@code InnerClasses} attribute.
 *
 * <p>Enigma takes nesting from class names, where {@code a/B$C} is an inner class of {@code a/B}.
 * Decompilers take it from the {@code InnerClasses} attribute, and only put a class' source inside another
 * class' source when that attribute says so. An obfuscator that strips the attribute without renaming makes
 * the two disagree: the class still reads as nested, but it decompiles to a file of its own.
 *
 * <p>A class counts as {@linkplain #isNestedInSource nested in source} when any class in the jar declares
 * an {@code InnerClasses} record naming it. The compiler writes that record in the nested class, in its
 * enclosing class and in every class that references it, so one record anywhere in the jar is enough.
 */
public class InnerClassIndex implements JarIndexer {
	private final Set<ClassEntry> nestedInSource = new HashSet<>();

	@Override
	public void indexInnerClass(ClassDefEntry classEntry, @NonNull InnerClassData innerClassData) {
		this.nestedInSource.add(new ClassEntry(innerClassData.name()));
	}

	/**
	 * Returns whether the passed {@code entry}'s source is written inside another class' source, i.e. whether the jar
	 * declares it nested with an {@code InnerClasses} record. A class whose name looks nested but that has no
	 * such record yields {@code false}.
	 *
	 * @param entry the class to check
	 */
	public boolean isNestedInSource(ClassEntry entry) {
		return this.nestedInSource.contains(entry);
	}

	/**
	 * Returns the class whose source contains the passed {@code entry}'s, which is {@code entry} itself unless it is
	 * {@linkplain #isNestedInSource nested in source}. This is the class to decompile, to open in an editor
	 * and to index tokens against when navigating to {@code entry}.
	 *
	 * @param entry the class to find the source of
	 */
	public ClassEntry getSourceRoot(ClassEntry entry) {
		ClassEntry root = entry;
		while (root.getOuterClass() != null && this.isNestedInSource(root)) {
			root = root.getOuterClass();
		}

		return root;
	}

	@Override
	public String getTranslationKey() {
		return "progress.jar.indexing.process.inner_classes";
	}
}
