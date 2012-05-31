package de.peeeq.eclipsewurstplugin.editor.highlighting;

import static de.peeeq.eclipsewurstplugin.WurstPlugin.SYNTAXCOLOR_COLOR;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import de.peeeq.eclipsewurstplugin.WurstPlugin;
import de.peeeq.eclipsewurstplugin.editor.WurstPartitionScanner;
import de.peeeq.eclipsewurstplugin.util.UtilityFunctions;


public class ScannerFactory {

	private ITokenScanner hotDocScanner;
	private ITokenScanner wurstCodeScanner;
	private ITokenScanner multilineCommentScanner;
	private IPreferenceStore preferencestore;

	public ScannerFactory() {
		preferencestore = UtilityFunctions.getDefaultPreferenceStore();
		wurstCodeScanner = new SimpleCodeScanner();
		hotDocScanner = new SingleTokenScanner(getStyle(WurstPlugin.SYNTAXCOLOR_COMMENT));
		multilineCommentScanner = new SingleTokenScanner(getStyle(WurstPlugin.SYNTAXCOLOR_COMMENT));
	}
	
	private TextAttribute getStyle(String key) {
		return new TextAttribute(
				new Color(Display.getCurrent(), 
						PreferenceConverter.getColor(preferencestore, SYNTAXCOLOR_COLOR+ key)), 
						null, 
						UtilityFunctions.computeAttributes(preferencestore, key));
	}

	public ITokenScanner wurstCodeScanner() {
		return new SimpleCodeScanner();
	}

	public ITokenScanner hotDocScanner() {
		return new SingleTokenScanner(getStyle(WurstPlugin.SYNTAXCOLOR_COMMENT));
	}

	public ITokenScanner multilineCommentScanner() {
		return new SingleTokenScanner(getStyle(WurstPlugin.SYNTAXCOLOR_COMMENT));
	}

	public IPartitionTokenScanner wurstPartitionScanner() {
		return new WurstPartitionScanner();                         
	}

}
