package com.example.android.activityrecognition;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.os.StrictMode;
import android.telephony.TelephonyManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class XMLWriter {

	String filename = "dataToSend.xml";
	File fXmlFile ;
	private static XMLWriter XMLWriterInstance = null;
	private Element rootElement;
	private Document doc;

	private XMLWriter(Context context) {
		System.out.println("create xml instance...");
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy); 
	}

	public void sendStats(String personId){
		File file = new File(Environment.getExternalStorageDirectory(), this.filename);
		if(file.exists()){   
			//post the file
			try{
				System.out.println("file is sending...");
				String x = this.xmlToString();
				webServiceConnection(x, personId);
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	private void webServiceConnection(String xmlString,String uniqueId) {

		String urlbase = "http://ec2-54-72-33-228.eu-west-1.compute.amazonaws.com:8080/ch.unifr.freiburgnet.ws/rest/plans/";
		// Form the URL
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(urlbase+uniqueId);
		try {
			StringEntity se = new StringEntity( xmlString, HTTP.UTF_8);
			se.setContentType("text/xml");
			httppost.setEntity(se);
			HttpResponse httpresponse = httpclient.execute(httppost);
			HttpEntity resEntity = httpresponse.getEntity();
			System.out.println(EntityUtils.toString(resEntity));  
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}


	private void createDocument(){
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			doc = docBuilder.newDocument();
			rootElement = (Element) doc.createElement("plan");
			doc.appendChild((Node) rootElement);
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(Environment.getExternalStorageDirectory(), this.filename));
			transformer.transform(source, result);

		} catch (ParserConfigurationException pce) {
			pce.printStackTrace();
		} catch (TransformerException tfe) {
			tfe.printStackTrace();
		}
	}

	protected void addActivity(String type, double x ,double y, String end_time) {
		File file = new File(Environment.getExternalStorageDirectory(), this.filename);
		if(!file.exists()){   
			createDocument();
		}
		try{
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			this.doc = docBuilder.parse(new File(Environment.getExternalStorageDirectory(), this.filename));

			// Get the root element
			Node plan = this.doc.getFirstChild();

			// append a new node to staff
			Element act = this.doc.createElement("act");
			act.setAttribute("type", type);
			act.setAttribute("x", x+"");
			act.setAttribute("y", y+"");
			if(end_time=="")
				System.out.println("");
			else
				act.setAttribute("end_time", end_time);
			plan.appendChild(act);

			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(this.doc);
			StreamResult result = new StreamResult(new File(Environment.getExternalStorageDirectory(), this.filename));
			transformer.transform(source, result);

		}catch(Exception e){
			e.printStackTrace();
		}

	}
	public boolean deleteFile(){
		File file = new File(Environment.getExternalStorageDirectory(), this.filename);
		boolean deleted = file.delete();
		return deleted;
	}

	protected void addLeg(String type) {
		File file = new File(Environment.getExternalStorageDirectory(), this.filename);
		if(!file.exists()){   
			createDocument();
		}
		try{
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			this.doc = docBuilder.parse(new File(Environment.getExternalStorageDirectory(), this.filename));
			// Get the root element
			Node plan = this.doc.getFirstChild();
			// append a new node to staff
			Element act = this.doc.createElement("leg");
			act.setAttribute("mode", type);
			plan.appendChild(act);
			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(this.doc);
			StreamResult result = new StreamResult(new File(Environment.getExternalStorageDirectory(), this.filename));
			transformer.transform(source, result);

		}catch(Exception e){
			e.printStackTrace();
		}

	}

	public static XMLWriter getInstance(Context context) {

		if (XMLWriterInstance == null) {
			XMLWriterInstance = new XMLWriter(context);
		}
		return XMLWriterInstance;
	}

	public String xmlToString(){
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer;
		String output ="";
		try {
			transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(this.doc), new StreamResult(writer));
			output = writer.getBuffer().toString().replaceAll("\n|\r", "");
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return output;

	}
}

