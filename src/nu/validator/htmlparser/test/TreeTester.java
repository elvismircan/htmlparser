package nu.validator.htmlparser.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import nu.validator.htmlparser.HtmlInputStreamReader;

import org.xml.sax.SAXException;

public class TreeTester {


    private final InputStream aggregateStream;

    private final StringBuilder builder = new StringBuilder();

    /**
     * @param aggregateStream
     */
    public TreeTester(InputStream aggregateStream) {
        this.aggregateStream = aggregateStream;
    }

    private void runTests() throws IOException, SAXException {
        while (runTest()) {
            // spin
        }
    }

    private boolean runTest() throws IOException, SAXException {
        if (skipLabel()) {
            return false;
        }
        UntilHashInputStream stream = new UntilHashInputStream(aggregateStream);
        HtmlInputStreamReader reader = new HtmlInputStreamReader(stream, null,
                null, null);
        Charset charset = reader.getCharset();
        stream.close();
        if (skipLabel()) {
            System.err.println("Premature end of test data.");
            return false;
        }
        builder.setLength(0);
        loop: for (;;) {
            int b = aggregateStream.read();
            switch (b) {
                case '\n':
                    break loop;
                case -1:
                    System.err.println("Premature end of test data.");
                    return false;
                default:
                    builder.append(((char) b));
            }
        }
        String sniffed = charset.name();
        String expected = builder.toString();
        if (expected.equalsIgnoreCase(sniffed)) {
            System.err.println("Success.");
            // System.err.println(stream);
        } else {
            System.err.println("Failure. Expected: " + expected + " got "
                    + sniffed + ".");
            System.err.println(stream);
        }
        return true;
    }

    private boolean skipLabel() throws IOException {
        int b = aggregateStream.read();
        if (b == -1) {
            return true;
        }
        for (;;) {
            b = aggregateStream.read();
            if (b == -1) {
                return true;
            } else if (b == 0x0A) {
                return false;
            }
        }
    }

    /**
     * @param args
     * @throws SAXException
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, SAXException {
        for (int i = 0; i < args.length; i++) {
            TreeTester tester = new TreeTester(new FileInputStream(
                    args[i]));
            tester.runTests();
        }
    }

    
}