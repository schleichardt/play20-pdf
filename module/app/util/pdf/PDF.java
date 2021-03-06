package util.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.CSSResource;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.resource.XMLResource;
import org.xhtmlrenderer.util.Configuration;

import play.Logger;
import play.api.Play;
import play.api.templates.Html;
import play.mvc.Result;
import play.mvc.Results;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;

public class PDF {

	public static class MyUserAgent extends ITextUserAgent {

		public MyUserAgent(ITextOutputDevice outputDevice) {
			super(outputDevice);
		}

		@Override
		public ImageResource getImageResource(String uri) {
			InputStream stream = Play.current().resourceAsStream(uri).get();
			if (stream == null)
				return super.getImageResource(uri);
			try {
				Image image = Image.getInstance(getData(stream));
				scaleToOutputResolution(image);
				return new ImageResource(new ITextFSImage(image));
			} catch (Exception e) {
				Logger.error("fetching image " + uri, e);
				throw new RuntimeException(e);
			}
		}

		@Override
		public CSSResource getCSSResource(String uri) {
			try {
				// uri is in fact a complete URL
				String path = new URL(uri).getPath();
				InputStream stream = Play.current().resourceAsStream(path)
						.get();
				if (stream == null)
					return super.getCSSResource(uri);
				return new CSSResource(stream);
			} catch (MalformedURLException e) {
				Logger.error("fetching css " + uri, e);
				throw new RuntimeException(e);
			}
		}

		@Override
		public byte[] getBinaryResource(String uri) {
			InputStream stream = Play.current().resourceAsStream(uri).get();
			if (stream == null)
				return super.getBinaryResource(uri);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				copy(stream, baos);
			} catch (IOException e) {
				Logger.error("fetching binary " + uri, e);
				throw new RuntimeException(e);
			}
			return baos.toByteArray();
		}

		@Override
		public XMLResource getXMLResource(String uri) {
			InputStream stream = Play.current().resourceAsStream(uri).get();
			if (stream == null)
				return super.getXMLResource(uri);
			return XMLResource.load(stream);
		}

		private void scaleToOutputResolution(Image image) {
			float factor = getSharedContext().getDotsPerPixel();
			image.scaleAbsolute(image.getPlainWidth() * factor,
					image.getPlainHeight() * factor);
		}

		private byte[] getData(InputStream stream) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			copy(stream, baos);
			return baos.toByteArray();
		}

		private void copy(InputStream stream, OutputStream os)
				throws IOException {
			byte[] buffer = new byte[1024];
			while (true) {
				int len = stream.read(buffer);
				os.write(buffer, 0, len);
				if (len < buffer.length)
					break;
			}
		}
	}
	public static Result ok(Html html) {
		byte[] pdf = toBytes(tidify(html.body()));
		return Results.ok(pdf).as("application/pdf");
	}

	public static byte[] toBytes(Html html) {
		byte[] pdf = toBytes(tidify(html.body()));
		return pdf;
	}

	private static String tidify(String body) {
		Tidy tidy = new Tidy();
		tidy.setXHTML(true);
		StringWriter writer = new StringWriter();
		tidy.parse(new StringReader(body), writer);
		return writer.getBuffer().toString();
	}

	public static byte[] toBytes(String string) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		toStream(string, os);
		return os.toByteArray();
	}

	public static void toStream(String string, OutputStream os) {
		try {
			Reader reader = new StringReader(string);
			ITextRenderer renderer = new ITextRenderer();
			renderer.getFontResolver().addFontDirectory(Play.current().path() + "/conf/fonts", BaseFont.EMBEDDED);
			MyUserAgent myUserAgent = new MyUserAgent(renderer.getOutputDevice());
			myUserAgent.setSharedContext(renderer.getSharedContext());
			renderer.getSharedContext().setUserAgentCallback(myUserAgent);
			Document document = XMLResource.load(reader).getDocument();
			renderer.setDocument(document, "http://localhost:9000");
			renderer.layout();
			renderer.createPDF(os);
		} catch (Exception e) {
			Logger.error("Creating document from template", e);
		}
	}

}
