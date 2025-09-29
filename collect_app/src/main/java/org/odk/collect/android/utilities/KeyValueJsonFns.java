package org.odk.collect.android.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

/*
 * This class contains miscellaneous utility functions
 */
public final class KeyValueJsonFns {
	
	public class KeyValue {
		public String key;
		public String value;
	}
	
	/*
	 * Accept a JSON string of key value pairs and return a comma separated list of values
	 */
	public static String getValues(String in) {
		StringBuilder out = new StringBuilder();
		
		if (in != null) {
	    	Gson gson = new GsonBuilder().create();
	    	Type type = new TypeToken<ArrayList<KeyValue>>(){}.getType();		
	    	ArrayList <KeyValue> kva = gson.fromJson(in, type);

			boolean hasEntries = false;
			if(kva != null && !kva.isEmpty()) {
				for (KeyValue kv : kva) {
					if (kv.key != null && kv.value != null) {
						if (hasEntries) {
							out.append(",");
						}
						out.append(kv.value);
						hasEntries = true;
					}
				}
			}
		}

		if(out.length() > 0) {
			return out.toString();
		} else {
			return null;
		}
	}
}
