package org.quiltmc.enigma.impl.source.vineflower;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.TextTokenVisitor;
import org.jspecify.annotations.Nullable;
import org.quiltmc.enigma.api.source.Source;
import org.quiltmc.enigma.api.source.SourceIndex;
import org.quiltmc.enigma.api.source.SourceSettings;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VineflowerSource implements Source {
	private final IContextSource contextSource;
	private final IContextSource libraryContextSource;
	private final boolean hasLibrarySource;
	private final String className;
	private EntryRemapper remapper;
	private final SourceSettings settings;

	private SourceIndex index;

	public VineflowerSource(EnigmaContextSource contextSource, EntryRemapper remapper, SourceSettings settings) {
		this(contextSource, contextSource.getExternalSource(), contextSource.getClassName(), remapper, settings);
	}

	public VineflowerSource(IContextSource contextSource, @Nullable IContextSource libraryContextSource, String className, EntryRemapper remapper, SourceSettings settings) {
		this.contextSource = contextSource;
		this.libraryContextSource = libraryContextSource;
		this.hasLibrarySource = libraryContextSource != null;
		this.className = className;
		this.remapper = remapper;
		this.settings = settings;
	}

	private static Map<String, Object> getOptions(IFabricJavadocProvider javadocProvider, SourceSettings settings) {
		Map<String, Object> options = VineflowerPreferences.getEffectiveOptions();
		options.put(IFabricJavadocProvider.PROPERTY_NAME, javadocProvider);

		if (settings.removeImports()) {
			options.put(IFernflowerPreferences.REMOVE_IMPORTS, "1");
		}

		return options;
	}

	@Override
	public String asString() {
		this.checkDecompiled();
		return this.index.getSource();
	}

	@Override
	public Source withJavadocs(EntryRemapper remapper) {
		this.remapper = remapper;
		this.index = null;
		return this;
	}

	@Override
	public SourceIndex index() {
		this.checkDecompiled();
		return this.index;
	}

	private void checkDecompiled() {
		if (this.index != null) {
			return;
		}

		this.index = new SourceIndex();

		EnigmaResultSaver saver = new EnigmaResultSaver(this.index);
		Map<String, Object> options = getOptions(new EnigmaJavadocProvider(this.remapper), this.settings);
		IFernflowerLogger logger = new EnigmaFernflowerLogger();
		BaseDecompiler decompiler = new BaseDecompiler(saver, options, logger);

		List<EnigmaTextTokenCollector> tokenCollectors = Collections.synchronizedList(new ArrayList<>());
		TextTokenVisitor.addVisitor(next -> {
			EnigmaTextTokenCollector collector = new EnigmaTextTokenCollector(next);
			tokenCollectors.add(collector);
			return collector;
		});
		decompiler.addSource(this.contextSource);
		if (this.hasLibrarySource) decompiler.addLibrary(this.libraryContextSource);

		decompiler.decompileContext();

		EnigmaTextTokenCollector tokenCollector = null;
		synchronized (tokenCollectors) {
			for (EnigmaTextTokenCollector collector : tokenCollectors) {
				if (collector.hasTokensFor(this.className)) {
					tokenCollector = collector;
					break;
				}
			}
		}

		if (tokenCollector == null) {
			Logger.warn("No tokens were collected for {}", this.className);
			return;
		}

		if (this.settings.removeImports()) {
			removePackageStatement(this.index, tokenCollector, this.className);
		} else {
			tokenCollector.addTokensToIndex(this.index, this.className, token -> token);
		}
	}

	private static void removePackageStatement(SourceIndex index, EnigmaTextTokenCollector tokenCollector, String className) {
		String source = index.getSource();
		int start = source.indexOf("package");
		if (start < 0) {
			tokenCollector.addTokensToIndex(index, className, token -> token);
			return;
		}

		int end = index.getPosition(index.getLineNumber(start) + 1, 1);
		int offset = -(end - start) - 1;

		String newSource = source.substring(0, start) + source.substring(end + 1);
		index.setSource(newSource);
		tokenCollector.addTokensToIndex(index, className, token -> {
			if (token.start > end) {
				return token.move(offset);
			} else if (token.end <= start) {
				return token;
			} else {
				return null;
			}
		});
	}
}
