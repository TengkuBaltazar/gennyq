package life.genny.qwandaq.utils;

import java.util.List;

import org.jboss.logging.Logger;

import life.genny.qwandaq.exception.GennyRuntimeException;
import life.genny.qwandaq.utils.callbacks.FIGetStringCallBack;

/**
 * A few Common Utils to use throughout Genny.
 *
 * @author Bryn
 * @author Jasper
 */
public class CommonUtils {
	static final Logger log = Logger.getLogger(CommonUtils.class);

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
     * Get a JSON style array of objects. Pass a callback for custom values. Will default to {@link Object#toString()} otherwise
     * @param <T> type
     * @param list - list to get array of
     * @param stringCallback - callback to use to retrieve a string value of the object
     * @return a JSON style array of objects, where each item is the value returned from stringCallback
     */
    public static <T> String getArrayString(List<T> list, FIGetStringCallBack<T> stringCallback) {
        String result = "";
        for(T object : list) {
            result += "\"" + stringCallback.getString(object) + "\",";
        }
        return "[" + result.substring(0, result.length() - 1) + "]";
    }

    /**
     * Get a JSON style array of objects. Pass a callback for custom values. Will default to {@link Object#toString()} otherwise
     * @param <T> type
     * @param array - list to get array of
     * @param stringCallback - callback to use to retrieve a string value of the object
     * @return a JSON style array of objects, where each item is the value returned from stringCallback
     */
    public static <T> String getArrayString(T[] array, FIGetStringCallBack<T> stringCallback) {
        String result = "";
        for(T object : array) {
            result += "\"" + stringCallback.getString(object) + "\",";
        }
        return "[" + result.substring(0, result.length() - 1) + "]";
    }

	/**
	 * Replace the three character prefix of a string with another prefix.
	 * @param str The string on which to perform replacement.
	 * @param prefix The prefix to replace with.
	 * @return The updated string
	 */
	public static String replacePrefix(String str, String prefix) {

		if (str.charAt(3) != '_') {
			throw new GennyRuntimeException(str + " is does not have a valid three character prefix");
		}
		return prefix + str.substring(3);
	}

}
