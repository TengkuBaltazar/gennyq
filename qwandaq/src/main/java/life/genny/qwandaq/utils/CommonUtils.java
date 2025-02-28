package life.genny.qwandaq.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import life.genny.qwandaq.exception.runtime.entity.GennyPrefixException;
import life.genny.qwandaq.utils.callbacks.FIGetObjectCallback;
import life.genny.qwandaq.utils.callbacks.FIGetStringCallBack;
import life.genny.qwandaq.utils.callbacks.FILogCallback;

/**
 * A few Common Utils to use throughout Genny.
 *
 * @author Bryn
 * @author Jasper
 */
public class CommonUtils {
	static final Logger log = Logger.getLogger(CommonUtils.class);

    /**
     * Normalize a String by forcing uppercase on first character and lowercase on the rest
     * e.g: 
     * <ul>
     *  <li>string -> String</li>
     *  <li>STRING -> String</li>
     * </ul>
     * @param string
     * @return
     */
	public static String normalizeString(String string) {
		return string.substring(0, 1).toUpperCase().concat(string.substring(1).toLowerCase());
	}

    /**
     * Log on a specific log level in a specific log and return an object
     * @param level - level to log on in the logger
     * @param msg - message to log
     * @return msg
     */
    public static Object logAndReturn(FILogCallback level, Object msg) {
        level.log(msg);
        return msg;
    }

    /**
     * Log info and return an object
     * @param log - log stream to log on (for class specific logs)
     * @param msg - message to log
     * @return msg
     */
    public static Object logAndReturn(Logger log, Object msg) {
        log.info(msg);
        return msg;
    }

    public static <T>void printCollection(Collection<T> collection, FILogCallback logCallback, FIGetStringCallBack<T> logLine) {
        for(T item : collection) {
            logCallback.log(logLine.getString(item));
        }
    }

    public static <T>void printCollection(Collection<T> collection, FIGetStringCallBack<T> logLine) {
        printCollection(collection, log::info, logLine);
    }

    public static <T>void printCollection(Collection<T> collection) {
        printCollection(collection, log::info, Object::toString);
    }

    /**
     * Prints a map over multiple lines
     * works well assuming that the toString methods of the keys and values are well defined
     * @param map map to print
     */
    public static void printMap(Map<?, ?> map) {
        for(Object key : map.keySet()) {
            log.info(key + "=" + map.get(key));
        }
    }


    /**
     * Safe-compare two Objects (null-safe)
     * @param <T> type
     * @param objA Object1 to compare
     * @param objB Object2 to compare
     * @return true if both strings are the same or false if not
     */
    public static <T> Boolean compare(T objA, T objB) {
        // Case string a is null
        if(objA == null) {
            return (objB == null);
        }

        // Case string b is null
        if(objB == null) {
            return (objA == null);
        }

        return objA.equals(objB);
    }

    /**
     * A method to retrieve a system environment variable, and optionally log it if it is missing (default, do log)
     * @param env Env to retrieve
     * @param alert whether or not to log if it is missing or not (default: true)
     * @return the value of the environment variable, or null if it cannot be found
     */
    public static String getSystemEnv(String env, boolean alert) {
        String result = System.getenv(env);
        if(result == null && alert) {
            String msg = "Could not find System Environment Variable: " + env;
            if(alert) {
                log.error(msg);
            } else {
                log.warn(msg);
            }
        }

        return result;
    }

    /**
     * A method to retrieve a system environment variable, and optionally log it if it is missing (default, do log)
     * @param env Env to retrieve
     * @return the value of the environment variable, or null if it cannot be found
     */
    public static String getSystemEnv(String env) {
        return getSystemEnv(env, true);
    }

    /**
     * Get a JSON style array of objects using {@link Object#toString()} for each object
     * @param <T> type
     * @param list - list to get stringified array of
     * @return a JSON style array of object
     */
    public static <T> String getArrayString(Collection<T> list) {
        return getArrayString(list, (item) -> item.toString());
    }

    /**
     * Get a JSON style array of objects using {@link Object#toString()} for each object
     * @param <T> type
     * @param arr - array to get stringified array of
     * @return a JSON style array of object
     */
    public static <T> String getArrayString(T[] arr) {
        return getArrayString(arr, (item) -> {
            return item.toString();
        });
    }

    /**
     * Get a JSON style array of objects. Pass a callback for custom values. Will default to {@link Object#toString()} otherwise
     * @param <T> type
     * @param list - list to get array of
     * @param stringCallback - callback to use to retrieve a string value of the object
     * @return a JSON style array of objects, where each item is the value returned from stringCallback
     */
    public static <T> String getArrayString(Collection<T> list, FIGetStringCallBack<T> stringCallback) {
        if(list == null)
            return "null";
        StringBuilder result = new StringBuilder("[");
        Iterator<T> iterator = list.iterator();
        for(int i = 0; i < list.size(); i++) {
            T object = iterator.next();
            result.append("\"")
                .append(stringCallback.getString(object));
            if(iterator.hasNext())
                result.append("\",");
            else
                result.append("\"");
        }

        // T object = iterator.next();
        // result.append("\"")
        //     .append(stringCallback.getString(object))
        result.append("]");
        return result.toString();
    }

    /**
     * Get a JSON style array of objects. Pass a callback for custom values. Will default to {@link Object#toString()} otherwise
     * @param <T> type
     * @param array - list to get array of
     * @param stringCallback - callback to use to retrieve a string value of the object
     * @return a JSON style array of objects, where each item is the value returned from stringCallback
     */
    public static <T> String getArrayString(T[] array, FIGetStringCallBack<T> stringCallback) {
        if(array == null) return "null";
        if(array.length == 0) return "[]";
        
        StringBuilder result = new StringBuilder("[");
        int i;
        for(i = 0; i < array.length - 1; i++) {
            result.append("\"")
            .append(stringCallback.getString(array[i]))
            .append("\",");
        }

        result.append("\"")
        .append(stringCallback.getString(array[i]))
        .append("\"]");
        
        return result.toString();
    }

    /**
     * Fetch an instance of a class from {@link io.quarkus.Arc}
     * @param <T>
     * @param clazz - Class to fetch instance for
     * @return the instance from CDI
     */
    public static <T> T getArcInstance(Class<T> clazz) {
        T instance = Arc.container().select(clazz).get();
        if(instance == null) {
            log.error("Could not find instance of " + clazz.getSimpleName() + " in the context!");
        }

        return instance;
    }

    public static <T> T[] getArrayFromString(String arrayString, FIGetObjectCallback<T> objectCallback) {
        return (T[])getListFromString(arrayString, objectCallback).toArray();
    }

    /**
     * Assuming arrayString is of the form "[a,b,c,d]"
     * @param <T>
     * @param arrayString
     * @param objectCallback
     * @return
     */
    public static <T> List<T> getListFromString(String arrayString, FIGetObjectCallback<T> objectCallback) {
        arrayString = arrayString.substring(1, arrayString.length() - 1).replaceAll("\"", "").strip();
        

		if(StringUtils.isBlank(arrayString))
            return new ArrayList<>(0);

        String components[] = arrayString.split(",");
        List<T> newList = new ArrayList<>(components.length);
        for(String component : components) {
            newList.add(objectCallback.getObject(component));
        }

        return newList;
    }


    /**
     * 
     * @param <T>
     * @param arrayString
     * @param objectCallback
     * @return
     */
    public static <T> Set<T> getSetFromString(String arrayString, FIGetObjectCallback<T> objectCallback) {
        String[] components = arrayString.substring(1, arrayString.length() - 1).replaceAll("\"", "").split(",");
        Set<T> newSet = new HashSet<>();
        for(String component : components) {
            newSet.add(objectCallback.getObject(component));
        }

        return newSet;
    }

    /**
     * Create an equals break (======) of size len
     * @param len length of the equals break
     * @return The equals string
     */
    public static String equalsBreak(int len) {
        StringBuilder ret = new StringBuilder();
        for(int i = 0; i < len; i++) {
            ret.append("=");
        }

        return ret.toString();
    }

    /**
     * Check if item is in array
     */
    public static <T>boolean isInArray(T[] array, T obj) {
        for(T o : array) {
            if(o.equals(obj))
                return true;
        }

        return false;
    }

    public static String cleanUpAttributeValue(String value) {
		String cleanCode = value.replace("\"", "").replace("[", "").replace("]", "").replace(" ", "");
		return cleanCode;
	}

	/**
	 * Replace the three character prefix of a string with another prefix.
	 * @param str The string on which to perform replacement.
	 * @param prefix The prefix to replace with.
	 * @return The updated string
	 */
	public static String replacePrefix(String str, String prefix) {

		if (str.charAt(3) != '_') {
			throw new GennyPrefixException(str + " is does not have a valid three character prefix");
		}
		return prefix + str.substring(3);
	}

	/**
	 * Remove a prefix from a string.
	 * @param str The string to operate on
	 * @return The string without the prefix
	 */
	public static String removePrefix(String str) {
		return str.substring(str.indexOf("_")+1);
	}


	// TODO: Going to elaborate on this more another time. Will allow for the extra _ character some constants have
	public static String substitutePrefix(String code, String prefix) {
		if(prefix.length() != 3) {
			log.error("Could not substitute prefix: " + prefix + ". Prefix length is not 3 characters");
			return code;
		}
		code = prefix + code.substring(prefix.length());
		return code;
	}

	/**
	 * Strip the prefix assuming there is a prefix of 3 characters on the code
	 * @param code
	 * @return the code without the prefix (if there is no prefix of 3 characters, there is no change)
	 */
	public static String safeStripPrefix(String code) {
		String[] components = code.split("_");
		if(components.length <= 1) { // no prefix
			return code;
		} else {
			if(components[0].length() != 3) {
				return code;
			}

			return code.substring(4);
		}
	}
}
