package nu.validator.htmlparser.dom;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.concurrent.TimeoutException;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.impl.ElementName;
import nu.validator.htmlparser.impl.ErrorReportingTokenizer;
import nu.validator.htmlparser.impl.HtmlAttributes;
import nu.validator.htmlparser.impl.Tokenizer;
import nu.validator.htmlparser.impl.UTF16Buffer;

public class ScriptedHtmlParser {

    public static interface DOMParseListener {
        void elementPopped(String ns, String name, Element node);
    
        void elementPushed(String ns, String name, Element node);
    
        void endTag(Element tag);

        void endDocument();
        
        String appendCharacters(Element parent, String text);
    }
    
    private class ScriptedDOMTreeBuilder extends DOMTreeBuilder {

        protected ScriptedDOMTreeBuilder(DOMImplementation implementation, Document document) {
            super(implementation, document);
        }

        private Method method;
        private boolean checkedForMethod;
        private Document document;
        private Method locationMethod;
        
        @Override
        protected void appendCharacters(Element parent, String text) throws SAXException {
            super.appendCharacters(parent, domParseListener.appendCharacters(parent, text));
        }
        
        @Override
        protected void elementPopped(String ns, String name, Element node) 
            throws SAXException {
                domParseListener.elementPopped(ns, name, node);
                super.elementPopped(ns, name, node);
        }
        
        @Override
        protected void elementPushed(String ns, String name, Element node)
                throws SAXException {
            domParseListener.elementPushed(ns, name, node);
            super.elementPushed(ns, name, node);
        }
        
        @Override
        public void endTag(ElementName elementName) throws SAXException {
            Element el = currentNode();
            super.endTag(elementName);
            domParseListener.endTag(el);
        }

        @Override
        protected void end() throws SAXException {
            try {
                super.end();
            } finally {
                domParseListener.endDocument();
            }
        }

        @Override
        protected Element createElement(String ns, String name, HtmlAttributes attributes, Element e) throws SAXException {
            checkForMethod();
            if (method != null) {
                try {
                    Element element = (Element) method.invoke(document, ns, name, attributes);
                    
                    // set line number
                    if ( locationMethod == null ) {
                        locationMethod = element.getClass().getMethod("setStartLocation", int.class, int.class);
                    }
                    locationMethod.invoke(element, 1, 1);
                    
                    return element;
                } catch ( Exception e1 ) {
                    // should never happen
                }
            }
            return super.createElement(ns, name, attributes, e);
        }

        /**
         * We need to do this because HTMLUnit needs the attributes passed in to create the correct type for inputs
         * @param document
         * @return
         */
        private Method checkForMethod() {
            if (checkedForMethod) {
                return this.method;
            }
            checkedForMethod = true;
            
            
            try {
                document = (Document) DOCUMENT_FIELD.get(this);
                // TODO: cache?
                this.method = document.getClass().getMethod("createElementNS", String.class, String.class, Attributes.class);
            } catch (Exception e) {
                // Should never happen
            }
            return this.method;

        }

    };

    private Tokenizer tokenizer;

    private StringBuilder writeBuffer = new StringBuilder();

    private final LinkedList<UTF16Buffer> bufferStack = new LinkedList<UTF16Buffer>();

    private UTF16Buffer currentBuffer;

    private DOMTreeBuilder domTreeBuilder;

    private DOMImplementation domImplementation;

    private DOMParseListener domParseListener;

    private boolean suspendRequested;

    private boolean noscript;


    public ScriptedHtmlParser(DOMImplementation domImplementation, DOMParseListener domParseListener, boolean noscript) {
        this.domImplementation = domImplementation;
        this.noscript = noscript;
        this.domParseListener = domParseListener;
    }

    private static final Field DOCUMENT_FIELD;
    
    static {
        Field field;
        try {
            field = DOMTreeBuilder.class.getDeclaredField("document");
            field.setAccessible(true);
        } catch (Throwable e) {
            e.printStackTrace();
            field = null;
        }
        DOCUMENT_FIELD = field;
    }
    
    /**
     * Parses a domImplementation from a SAX <code>InputSource</code>.
     * 
     * @param is
     *            the source
     * @return
     * @return the doc
     * @see javax.xml.parsers.NodeBuilder#parse(org.xml.sax.InputSource)
     */
    public void parse(Document document, String source, Node context) throws SAXException {

        if (context instanceof DocumentFragment) {
            setup(null);
            domTreeBuilder.setScriptingEnabled(true);   
            domTreeBuilder.setFragmentContext("#document-fragment");
        } else {
            setup(document);
            domTreeBuilder.setScriptingEnabled(!noscript); 
            if (context instanceof Element) {
                // this is always lowercased
                String ctx = ((Element) context).getTagName().intern();
                String namespaceURI = context.getNamespaceURI();
                if ( namespaceURI == null || namespaceURI.isEmpty() ) {
                    namespaceURI = "http://www.w3.org/1999/xhtml";
                }
                domTreeBuilder.setFragmentContext(ctx, namespaceURI.intern(), (Element) context, false);
            } else {
                domTreeBuilder.setFragmentContext(null);
            }
        }
        
        tokenize(source);
        
        if (context instanceof DocumentFragment) {
            Document tempDocument = domTreeBuilder.getDocument();
            Node rootElt = tempDocument.getFirstChild();
            while (rootElt.hasChildNodes()) {
                context.appendChild(rootElt.getFirstChild());
            }
        }
    }

    private void setup(Document document) {
        this.domTreeBuilder = new ScriptedDOMTreeBuilder(domImplementation, document);
        this.tokenizer = new ErrorReportingTokenizer(domTreeBuilder);
        this.domTreeBuilder.setNamePolicy(XmlViolationPolicy.ALLOW);
        this.tokenizer.setCommentPolicy(XmlViolationPolicy.ALLOW);
        this.tokenizer.setContentNonXmlCharPolicy(XmlViolationPolicy.ALLOW);
        this.tokenizer.setContentSpacePolicy(XmlViolationPolicy.ALLOW);
        this.tokenizer.setNamePolicy(XmlViolationPolicy.ALLOW);
        this.tokenizer.setXmlnsPolicy(XmlViolationPolicy.ALLOW);
    }


    /**
     * @param is
     * @throws SAXException
     * @throws IOException
     * @throws MalformedURLException
     */
    private void tokenize(String source) throws SAXException {
        bufferStack.push(new UTF16Buffer(source.toCharArray(), 0, source.length()));
        tokenizer.start();
        pump();
    }

    public void pump() throws SAXException {
    	
    	// This is guarding against infinite loops of JS doing document.write()
    	long maxTime = 2000;
    	long start = System.currentTimeMillis();
    	while ( ! Thread.currentThread().isInterrupted() && doPump() ) {
    		if ( System.currentTimeMillis() - start > maxTime ) {
    			throw new RuntimeException("pump() took too long to process");
    		}
    	}
    }
    
    public boolean doPump() throws SAXException {

        suspendRequested = false;
        
        if (writeBuffer.length() > 0) {
            String source = writeBuffer.toString();
            writeBuffer.setLength(0);
            bufferStack.push(new UTF16Buffer(source.toCharArray(), 0, source.length()));
        }

        currentBuffer = bufferStack.peek();

        if (currentBuffer == null) {
            tokenizer.eof();
            tokenizer.end();
            return false;
        }

        if (currentBuffer.hasMore()) {
            tokenizer.tokenizeBuffer(currentBuffer);
            if ( suspendRequested ) { // onResume is set if we have requested a suspension
                return false;
            }
        }
        
        if (! currentBuffer.hasMore() ) {
        	bufferStack.pop();
        }
        
        
        return true;
    }

    public void injectWriteBuffer() {
        if ( writeBuffer.length() > 0 ) {
            // we have called domImplementation.write, so we need to switch to the newer
            // buffer
            tokenizer.requestSuspension();
        }
    }
    
    public void clearWriteBuffer() {
        writeBuffer.setLength(0);
    }
    
    public void suspend() {
        suspendRequested = true;
        tokenizer.requestSuspension();
    }

    public void documentWrite(String text) {
        writeBuffer.append(text);
    }

    public void parse(String input) throws SAXException {
        parse(null, input, null);
    }

}