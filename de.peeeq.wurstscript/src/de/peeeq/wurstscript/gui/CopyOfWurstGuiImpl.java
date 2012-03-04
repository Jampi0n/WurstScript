package de.peeeq.wurstscript.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.google.common.collect.Lists;

import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.utils.Utils;

public class CopyOfWurstGuiImpl implements WurstGui {

	private volatile Queue<CompileError> errorQueue = new ConcurrentLinkedQueue<CompileError>();
	private List<CompileError> errors = Lists.newLinkedList(); // this is not concurrent, because we only use this list from the main thread
	private volatile double progress = 0.0;
	private volatile boolean finished = false;
	private volatile String currentlyWorkingOn = "";

	
	@Override
	public int getErrorCount() {
		return errors.size();
	}

	@Override
	public String getErrors() {
		return Utils.join(errors, "\n");
	}
	
	class TheGui extends JFrame implements Runnable {
		private boolean errors = false; // shadow the errors variable, so we do not use it by accident
		private static final long serialVersionUID = 1501435979514614061L;
		private DefaultListModel errorListModel;
		private JProgressBar progressBar;
		private JTextPane codeArea;
		private String currentFile = "";
		private ArrayList<Integer> currentFileLineList;
		private JTextArea errorDetailsPanel;

		public TheGui() {
			super("WurstScript");

			try {
				UIManager.setLookAndFeel(
						UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				// well, we can live with the ugly style if we cannot load the cool one
			}
			this.setSize(800, 600);
			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
         
	        // Determine the new location of the window
	        int w = getSize().width;
	        int h = getSize().height;
	        int x = (dim.width-w)/2;
	        int y = (dim.height-h)/2;
	         
	        // Move the window
	        setLocation(x, y);

			JPanel pane = new JPanel();
			pane.setLayout(new BoxLayout(pane, BoxLayout.PAGE_AXIS));
			pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			pane.add(progressBar());
			pane.add(Box.createRigidArea(new Dimension(0,5)));
			pane.add(errorView());
			pane.add(Box.createRigidArea(new Dimension(0,5)));
			
			pane.add(errorDetailsPanel() );
			pane.add(Box.createRigidArea(new Dimension(0,5)));
			pane.add(sourceView());
			

			add(pane);
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			
			//			pack();
			setVisible(true);
		}

		
		private Component errorDetailsPanel() {
			errorDetailsPanel = new JTextArea();
			errorDetailsPanel.setLineWrap(true);
			errorDetailsPanel.setWrapStyleWord(true);
			errorDetailsPanel.setEditable(false);
			errorDetailsPanel.setFont(this.getFont());
			errorDetailsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
			errorDetailsPanel.setBackground(this.getBackground());
			JScrollPane scrollPane = new JScrollPane(errorDetailsPanel);
			scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scrollPane.setPreferredSize(new Dimension(500, 50));
			scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
			return scrollPane;
		}

		private Component errorView() {
			errorListModel =  new DefaultListModel();
			final JList errorList = new JList( errorListModel);

			errorList.addListSelectionListener(new ListSelectionListener() {

				@Override
				public void valueChanged(ListSelectionEvent e) {
					int index = errorList.getSelectedIndex();
					if (index >= 0) {
						CompileError err = (CompileError) errorListModel.get(index);
						viewErrorDetail(err);
					}
				}


			});

			JScrollPane scrollPane = new JScrollPane(errorList);
			scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scrollPane.setPreferredSize(new Dimension(500, 200));
			return scrollPane;
		}

		private Component sourceView() {
			codeArea = new JTextPane();
			codeArea.setEditable(false);
			JScrollPane scrollPane = new JScrollPane(codeArea);
			scrollPane.setPreferredSize(new Dimension(500, 300));
			return scrollPane;
		}

		private Component progressBar() {
			progressBar = new JProgressBar();
			progressBar.setMinimum(0);
			progressBar.setMaximum(100);
			return progressBar;
		}

		private void viewErrorDetail(CompileError err) {
			this.errorDetailsPanel.setText(err.getMessage());
			
			String fileName = err.getSource().getFile();
			int lineNr = err.getSource().getLine();
			int column = err.getSource().getColumn();

			try {
				if (!currentFile.equals(fileName)) {
					currentFile = fileName;
					currentFileLineList = new ArrayList<Integer>();
					FileReader fr = new FileReader(fileName);
					codeArea.read(fr, fileName);
					
					// caculate line numbers:
					String text = codeArea.getText();
					currentFileLineList.add(0);
					int pos = 0;
					while (pos >= 0) {
						currentFileLineList.add(pos);
						pos = text.indexOf("\n", pos+1); 						
					}
				}

				String text = codeArea.getText();

				MutableAttributeSet attrs = codeArea.getInputAttributes();
				StyleConstants.setUnderline(attrs, false);
				StyleConstants.setBackground(attrs, new Color(255, 255, 255));
				// reset highlighting
				codeArea.getStyledDocument().setCharacterAttributes(0, text.length()-1, attrs , true);

				int ignoredChars = lineNr - 2; // fix for newlines
				int selectionStart = currentFileLineList.get(lineNr) + column;
				// select at least one character:
				int selectionEnd = Math.min(text.length()-1, selectionStart + 1);
				// try to select an identifier or something:
				while (selectionEnd < text.length()) {
					selectionEnd++;
					if (!Character.isJavaIdentifierPart(text.charAt(selectionEnd))) {
						break;
					}
				}
				
				// correct ignored chars:
				selectionStart -= ignoredChars;
				selectionEnd -= ignoredChars;


				StyleConstants.setUnderline(attrs, true);
				StyleConstants.setBackground(attrs, new Color(255, 150, 150));
				StyledDocument doc = codeArea.getStyledDocument();
				doc.setCharacterAttributes(selectionStart, selectionEnd-selectionStart, attrs, true);

				codeArea.select(selectionStart, selectionEnd);

			} catch (FileNotFoundException e) {
				codeArea.setText("Could not load file: " + fileName);
			} catch (IOException e) {
				codeArea.setText("Could not read file: " + fileName);
			}
		}

		@Override
		public void run() {
			while (!finished || !errorQueue.isEmpty()) {
				Utils.sleep(500);

				try {
					// Update the UI:
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {
							for (CompileError elem = errorQueue.poll() ;elem != null; elem = errorQueue.poll()) {
								if (errorListModel.isEmpty()) {
									viewErrorDetail(elem);
								}
								errorListModel.addElement(elem);

							}
							progressBar.setValue((int) (progress*100));
							setTitle("WurstScript - " + currentlyWorkingOn);
						}
					});
				} catch (Exception e) {
					throw new Error(e);
				}
			}
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						progressBar.setValue(100);
						progressBar.setEnabled(false);
						setTitle("Compilation finished.");
						
						if (errorListModel.size() == 0) {
							// if we have no errors we can just quit
							dispose();							
						}
					}
				});
			} catch (Exception e) {
				WLogger.severe(e.toString());
				throw new Error(e);
			}
		}


	}

	public CopyOfWurstGuiImpl() {
		TheGui gui = new TheGui();
		new Thread(gui).start();
	}


	@Override
	public void sendError(CompileError err) {
		errorQueue.add(err);
		errors.add(err);
	}

	@Override
	public void sendProgress(String message, double percent) {
		if (message != null) {
			WLogger.info(message);
			this.currentlyWorkingOn = message;
		}
		if (percent >= 0.0 && percent <= 1.0) {
			progress = percent;
		}
	}

	@Override
	public void sendFinished() {
		currentlyWorkingOn = "Finished";
		progress = 1.0;
		finished = true;
	}

	@Override
	public List<CompileError> getErrorList() {
		return Lists.newLinkedList(errors);
	}
}