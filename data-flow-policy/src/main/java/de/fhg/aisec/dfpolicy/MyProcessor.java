package de.fhg.aisec.dfpolicy;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyProcessor implements AsyncProcessor {
	
    private static final Logger LOG = LoggerFactory.getLogger(MyProcessor.class);  
    private Processor target;
    private static Set<LabelingRule> labelRules = null;
    private static Set<LabelingRule> removeLabelRules = null;
    private static Set<AllowRule> allowRules = null;
    
    
    public MyProcessor(Processor target) {
    	this.target = target;
    	//If we didn't load the rules yet, do it now
    	if (labelRules == null || allowRules == null) {
    		labelRules = new HashSet<LabelingRule>();
    		removeLabelRules = new HashSet<LabelingRule>();
    		allowRules = new HashSet<AllowRule>();
    		loadRules("deploy/rules");
    	}
    }
    
    
    private void loadRules(String rulefile) {

    	System.out.println("Start 'loadRules'...");
    	BufferedReader bufferedreader;
    	String line, attribute, label;
    	Set<String> label_set = new HashSet<String>();
 
    	try {
	    	bufferedreader = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(rulefile))));
			while ((line = bufferedreader.readLine()
					// remove empty spaces
					.replaceAll(" ", "")
					// remove one-line-comments starting with //
					.replaceAll("//.*?\n","\n")
					// remove one-line-comments starting with #
					.replaceAll("#.*?\n","\n")
					) != null)   {

					//Check if it is a LABEL-rule that contains LABEL and AS, and both only once
					if (checkRuleSyntax(line, Constants.LABEL, Constants.AS)) {
						System.out.println("----------------------------------");
						label_set.clear();
						// source = the string between the first and the second keyword 
						attribute = line.substring(line.indexOf(Constants.LABEL) + Constants.LABEL.length(), line.indexOf(Constants.AS));

						// label = the string after the second keyword
						label = line.substring(line.indexOf(Constants.AS) + Constants.AS.length());
						label_set = splitString(label, ",");
						labelRules.add(new LabelingRule(attribute, label_set));
						
						System.out.println("labelRules - label_set: " + joinStringSet(label_set, ","));
						System.out.println("labelRules - all: " + joinStringSetLabelRule(labelRules, ","));
						System.out.println("labelRules.size() : " + labelRules.size());
						System.out.println("----------------------------------");
					} 
					// Check for an REMOVELABEL-rule
					else if (checkRuleSyntax(line, Constants.REMOVELABEL, Constants.FROM)) {
						System.out.println("----------------------------------");
						label_set.clear();
						// label = the string between the first and the second keyword 
						label = line.substring(line.indexOf(Constants.REMOVELABEL) + Constants.REMOVELABEL.length(), line.indexOf(Constants.FROM));
			
						// source = the string after the second keyword
						attribute = line.substring(line.indexOf(Constants.FROM) + Constants.FROM.length());
						label_set = splitString(label, ",");
						removeLabelRules.add(new LabelingRule(attribute, label_set));
						
						System.out.println("removeLabelRules - label_set: " + joinStringSet(label_set, ","));
						System.out.println("removeLabelRules - all: " + joinStringSetLabelRule(removeLabelRules, ","));
						System.out.println("removeLabelRules.size() : " + removeLabelRules.size());
						System.out.println("----------------------------------");
					}
					// Check for an ALLOW-rule
					else if (checkRuleSyntax(line, Constants.ALLOW, Constants.TO)) {
						System.out.println("----------------------------------");
						label_set.clear();
						// label = the string between the first and the second keyword 
						label = line.substring(line.indexOf(Constants.ALLOW) + Constants.ALLOW.length(), line.indexOf(Constants.TO));
						
						// destination = the string after the second keyword
						attribute = line.substring(line.indexOf(Constants.TO) + Constants.TO.length());
						label_set = splitString(label, ",");
						allowRules.add(new AllowRule(label_set, attribute));
						
						System.out.println("allowRules - label_set: " + joinStringSet(label_set, ","));
						System.out.println("allowRules - all: " + joinStringSetAllowRule(allowRules, ","));
						System.out.println("allowLabelRules.size() : " + allowRules.size());
						System.out.println("----------------------------------");
					} 
					// skip if line is empty (or has just comments)
					else if (line.isEmpty()) {
						
					} 
					// otherwise log error
					else {
						LOG.error("Error: Could not parse line " +line + " from rules file");
						System.out.println("Error: Could not parse line " +line + " from rules file");
					} 
				}
			} catch (IOException e) {
				LOG.error("Caught IOException: " + e.getMessage());
				System.out.println("Caught IOException: " + e.getMessage());
				e.printStackTrace();
			}
			
		LOG.info("Loaded LABEL rules: " + labelRules.toString());
		LOG.info("Loaded REMOVELABEL rules: " + removeLabelRules.toString());
		LOG.info("Loaded ALLOW rules: " + allowRules.toString()); 		
    	System.out.println("Stop'loadRules'...");
    }
    
    // Splits the String at the seperator and stores the separated values in a Set<String>
    public Set<String> splitString(String label, String separator) {
    	String[] labels_tmp = null;
    	Set<String> label_set = new HashSet<String>();
    	
		if (label.contains(separator))
		{
			labels_tmp = label.split(separator);

			for (int i = 0; i < labels_tmp.length; i++)
			{
				label_set.add(labels_tmp[i]);
			}
			
		} else 
			label_set.add(label);
		
		return label_set;
    }
    
    // Checks for a line in the rule-file that each keyword exists only once, keyword1 before keyword 2, etc... 
    public boolean checkRuleSyntax(String line, String keyword1, String keyword2){
		//keyword1 in the beginning?
    	if (line.startsWith(keyword1)
    			//keyword 2 exists?
				&& line.contains(keyword2)
				//no second keyword1?
				&& line.lastIndexOf(keyword1) == 0
				//no second keyword2?
				&& line.indexOf(keyword2) == line.lastIndexOf(keyword2)) {
    				return true;
				} else {
					return false;
				}
    }
    
    public void process(Exchange exchange) throws Exception {
		System.out.println("Loaded LABEL rules: " + joinStringSetLabelRule(labelRules, ","));
		System.out.println("Loaded REMOVELABEL rules: " + joinStringSetLabelRule(removeLabelRules, ","));
		System.out.println("Loaded ALLOW rules: " + joinStringSetAllowRule(allowRules, ",")); 		
		System.out.println("-----------------------------------");
		System.out.println("Start 'process' with endpoint ..." + exchange.getFromEndpoint().getEndpointUri());
		
		InstrumentationProcessor instrumentationprocessor;
		SendProcessor sendprocessor;
		String destination;
		String exchange_labels_raw;
		Set<String> exchange_labels = new HashSet<String>();
		String body;
		//label the new message if needed
		exchange = LabelingProcess(exchange);
		// set labels from Property
		exchange_labels_raw = (exchange.getProperty("labels") == null) ? "" : exchange.getProperty("labels").toString();

		exchange_labels = splitString(exchange_labels_raw, ",");
		
		//figuring out where the message should go to
		if (target instanceof InstrumentationProcessor) {
			instrumentationprocessor = (InstrumentationProcessor) target;
			if (instrumentationprocessor.getProcessor() instanceof SendProcessor) {
				sendprocessor = (SendProcessor) instrumentationprocessor.getProcessor();
				destination = sendprocessor.getEndpoint().getEndpointUri();
				
			//if it's also no LogProcessor, throw an Error
			} else if (instrumentationprocessor.getProcessor() instanceof LogProcessor) {
				//nothing to do yet, maybe some logging later 
				return;
			} else {
				System.out.println("target is neither an instance of Send- nor Log-Processor: " + target.toString());
				LOG.error("target is neither an instance of Send- nor Log-Processor: " + target.toString());
				return;
			}
		} else {
			System.out.println("target is not an instance of InstrumentactionProcessor");
			LOG.error("target is not an instance of InstrumentactionProcessor");
			return;
		}
		
		System.out.println("********************************************");
		System.out.println("START: Check if label(s) exist in allow rules for destination " + destination);
		boolean destinationAndRuleMatch = false;
		for (AllowRule rule : allowRules) {
			System.out.println("--------------------------------------------");
			System.out.println("Allow rule: " + rule.getLabel());
			//match for destination
			Pattern pattern = Pattern.compile(rule.getDestination());
			Matcher matcher = pattern.matcher(destination);
			
			System.out.println("pattern: " + rule.getDestination());
			System.out.println("matcher: " + destination);
			
			if (matcher.find()) {
				//the destination matches, now let's see if the label matches
				//Check if the message has the required labels. If not, we stop 
				if (!checkIfLabelsExist(rule.getLabel(), exchange_labels)) {
					System.out.println("Required label(s) '" + exchange_labels_raw + "' not found, message will be dropped...");
					continue;
				} else {
					destinationAndRuleMatch = true;
					System.out.println("Destination '" + destination + "' and label(s) '" + exchange_labels_raw + "' match.");
					System.out.println("--------------------------------------------");
					break;
				}
					
			} else {
				System.out.println("Destination does not match.");
				System.out.println("--------------------------------------------");
				continue;
			}
		}
		System.out.println("STOP: Check if label(s) exist in allow rules.");
		System.out.println("********************************************");

		if (destinationAndRuleMatch)
		{
			System.out.println("Message with labels  '" + exchange_labels_raw +"' has all required labels for destination '" + destination + "', forwarding...");

			//store labels in message body
			body = exchange.getIn().getBody().toString();
			if (body.startsWith("Labels: ") && body.contains("\n\n")) {
				body = body.substring(body.indexOf("\n\n") + "\n\n".length(), body.length() - 1 );
			}
			exchange.getIn().setBody("Labels: " + exchange_labels_raw + "\n\n" + body);
					
			target.process(exchange);	
		}
		
		System.out.println("Stop 'process' with endpoint ..." + exchange.getFromEndpoint().getEndpointUri());
    }
	
	//check if a label exists in a set of labels
	public boolean checkIfLabelExists(Set<String> label_set, String label){
		System.out.println("Start 'checkIfLabelExists' ...");
		System.out.println("label_set: " + label_set);
		System.out.println("check_label: " + label);
		
		//if there are no requirements we have to fulfill, we return true
		if (label == null) {
			System.out.println("label = null");
			System.out.println("Stop 'checkIfLabelExists' ...");
				
			return true;
		}
		
		//if label is null, but labels isn't, we return false
		if (label_set == null) {
			System.out.println("label != null, label_set == null");
			System.out.println("Stop 'checkIfLabelExists' ...");
			return false;
		}

		Pattern pattern = Pattern.compile(label);
		Matcher matcher = pattern.matcher(label_set.toString());

		//check if the label is contained in the requirements. If not, return false;
		if (matcher.find()) {
			System.out.println("matcher.find() = true");
			System.out.println("Stop 'checkIfLabelExists' ...");
			return true;
		} else {
			System.out.println("matcher.find() = false");
			System.out.println("Stop 'checkIfLabelExists' ...");
			return false;
		}
	}
	
	// Check if a set of labels exist in another set of labels
	public boolean checkIfLabelsExist(Set<String> label_set, Set<String> check_labels) {
		System.out.println("Start 'checkIfLabelsExists' ...");
		System.out.println("label_set: " + label_set);
		System.out.println("check_labels " + check_labels);
		
		if (check_labels.isEmpty())
			return false;
		
		for (String s : check_labels) {
			if (!checkIfLabelExists(label_set, s)) {
				return false;
			}
		}

		return true;
	}
	
	
	public Exchange LabelingProcess(Exchange exchange) {
		System.out.println("Start 'LabelingProcess' ...");
		
		Set<String> exchange_label_set = new HashSet<String>();
		String body = exchange.getIn().getBody().toString();
		String labels;
		
		if (exchange.getProperty("labels") != null ) {
			exchange_label_set.add(exchange.getProperty("labels").toString());
		}
		
		//Check if there are some labels in the body we have to use
		if (body.startsWith("Labels: ")) {
			labels = body.substring("Labels: ".length(), body.indexOf("\n"));
			System.out.println("Found labels in exchange body: " +labels);

			List<String> items = Arrays.asList(labels.split("\\s*,\\s*"));
			exchange_label_set.addAll(items);
		}
		
		//Check if we have to remove some labels based on the source
		exchange_label_set = removeLabelBasedOnAttributeToSet(exchange_label_set, exchange.getFromEndpoint().getEndpointUri());
		
		//Check if we have to remove some labels based on the name
		exchange_label_set = removeLabelBasedOnAttributeToSet(exchange_label_set, exchange.getIn().toString());
		
		//Check if we have a labeling rule for this source
		exchange_label_set = addLabelBasedOnAttributeToSet(exchange_label_set, exchange.getFromEndpoint().getEndpointUri());
		
		//Check if we have a labeling rule for this name
		exchange_label_set = addLabelBasedOnAttributeToSet(exchange_label_set, exchange.getIn().toString());
		
		exchange.setProperty("labels", joinStringSet(exchange_label_set, ","));
		System.out.println("Labels in message: " + joinStringSet(exchange_label_set, ","));

		System.out.println("Stop 'LabelingProcess' ...");
		
		return exchange;
	}

	public Set<String> addLabelBasedOnAttributeToSet(Set<String> exchange_label_set, String attribute){
		System.out.println("Start 'addLabelBasedOnAttributeToSet' ...");
		System.out.println("labelRules: " + joinStringSetLabelRule(labelRules, ","));
		
		for (LabelingRule rule : labelRules) {
			String rule_attribute = rule.getAttribute();
			Set<String> label = rule.getLabel();
			Pattern pattern = Pattern.compile(rule_attribute);
			Matcher matcher = pattern.matcher(attribute);
			
			if (matcher.find()) {
				exchange_label_set.addAll(label);
				System.out.println("Got a message with attribute '" + attribute + "' matching pattern '" + rule_attribute + "', assigning label '" + label + "'. All labels are now: '" + joinStringSet(exchange_label_set, ",") + "'");				
			}
		}
		
		System.out.println("Stop 'addLabelBasedOnAttributeToSet' ...");
		
		return exchange_label_set;
	}

	public Set<String> removeLabelBasedOnAttributeToSet(Set<String> exchange_label_set, String attribute){
		System.out.println("Start 'removeLabelBasedOnAttributeToSet' ...");
		
		//No labels to remove here
		if (exchange_label_set.isEmpty()) {
			return exchange_label_set;
		}

		System.out.println("removeLabelRules: " + joinStringSetLabelRule(removeLabelRules, ","));
		
		for (LabelingRule rule : removeLabelRules) {
			String rule_attribute = rule.getAttribute();
			Set<String> label = rule.getLabel();
			Pattern pattern = Pattern.compile(rule_attribute);
			Matcher matcher = pattern.matcher(attribute);
			
			if (matcher.find()) {
				exchange_label_set.removeAll(label);
				System.out.println("Got a message with attribute '" + attribute + "' matching pattern '" + rule_attribute + "', removed label '" + label + "'. All labels are now: '" + joinStringSet(exchange_label_set, ",") + "'");
			}
		}

		System.out.println("Stop 'removeLabelBasedOnAttributeToSet' ...");
		
		return exchange_label_set;
	}

	// Join strings from a Set<String> with a separator
	public static String joinStringSet(Set<String> set, String separator) {
		StringBuilder builder = new StringBuilder();
		
		for (String string : set) {
		  builder.append(string).append(separator);
		}
		
		if (builder.length() >= separator.length())
			builder.setLength(builder.length() - separator.length());
		
		return builder.toString();
	}
	
	// Join LabelingRule-Strings from a Set with a separator
    public static String joinStringSetLabelRule(Set<LabelingRule> labelRules, String separator)
    {
    	StringBuilder builder = new StringBuilder();

		for (LabelingRule string : labelRules) {
			  builder.append("(").append(string.getLabel().toString()).append(",").append(string.getAttribute()).append(")").append(separator);
			}

		if (builder.length() >= separator.length())
			builder.setLength(builder.length() - separator.length());
		
		return builder.toString();
    }

	// Join AllowRule-Strings from a Set with a separator
    public static String joinStringSetAllowRule(Set<AllowRule> allowRules, String separator)
    {
    	StringBuilder builder = new StringBuilder();

    	for (AllowRule string : allowRules) {
			  builder.append("(").append(string.getLabel().toString()).append(",").append(string.getDestination()).append(")").append(separator);
			}

		if (builder.length() >= separator.length())
			builder.setLength(builder.length() - separator.length());
		
		return builder.toString();
    }

	@Override
    public String toString() {
      return "MyProcessor[" + 
    		  "allow:" + MyProcessor.allowRules.toString() +
    		  "label:" + MyProcessor.labelRules.toString() +
    		  "remove:" + MyProcessor.removeLabelRules.toString() +
    		  "]";
    }
	
	@Override
	public boolean process(Exchange exchange, AsyncCallback ac) {
		System.out.println("Start ...");
		try {
			process(exchange);
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Stop ...");
		return true;
	}
 
}