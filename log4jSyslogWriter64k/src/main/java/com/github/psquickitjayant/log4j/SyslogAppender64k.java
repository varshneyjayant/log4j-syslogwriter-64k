package com.github.psquickitjayant.log4j;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.SyslogQuietWriter;
import org.apache.log4j.spi.LoggingEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;

import com.github.psquickitjayant.log4j.helpers.SyslogWriter64k;


/**
    Use SyslogAppender64k to send log messages upto 64K to a remote syslog daemon.

*/
public class SyslogAppender64k extends AppenderSkeleton {
  // The following constants are extracted from a syslog.h file
  // copyrighted by the Regents of the University of California
  // I hope nobody at Berkley gets offended.

  /** Kernel messages */
  final static public int LOG_KERN     = 0;
  /** Random user-level messages */
  final static public int LOG_USER     = 1<<3;
  /** Mail system */
  final static public int LOG_MAIL     = 2<<3;
  /** System daemons */
  final static public int LOG_DAEMON   = 3<<3;
  /** security/authorization messages */
  final static public int LOG_AUTH     = 4<<3;
  /** messages generated internally by syslogd */
  final static public int LOG_SYSLOG   = 5<<3;

  /** line printer subsystem */
  final static public int LOG_LPR      = 6<<3;
  /** network news subsystem */
  final static public int LOG_NEWS     = 7<<3;
  /** UUCP subsystem */
  final static public int LOG_UUCP     = 8<<3;
  /** clock daemon */
  final static public int LOG_CRON     = 9<<3;
  /** security/authorization  messages (private) */
  final static public int LOG_AUTHPRIV = 10<<3;
  /** ftp daemon */
  final static public int LOG_FTP      = 11<<3;

  // other codes through 15 reserved for system use
  /** reserved for local use */
  final static public int LOG_LOCAL0 = 16<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL1 = 17<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL2 = 18<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL3 = 19<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL4 = 20<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL5 = 21<<3;
  /** reserved for local use */
  final static public int LOG_LOCAL6 = 22<<3;
  /** reserved for local use*/
  final static public int LOG_LOCAL7 = 23<<3;

  protected static final int SYSLOG_HOST_OI = 0;
  protected static final int FACILITY_OI = 1;

  /**
   * Max lengths in bytes of a message. Per RFC 5424, size limits are dictated
   * by the syslog transport mapping in use. But, the practical upper limit of
   * UDP over IPV4 is 65507 (65535 − 8 byte UDP header − 20 byte IP header).
   */
  protected static final int LOWER_MAX_MSG_LENGTH = 480;
  protected static final int UPPER_MAX_MSG_LENGTH = 65507;

  static final String TAB = "    ";

  // Have LOG_USER as default
  int syslogFacility = LOG_USER;
  String facilityStr;
  boolean facilityPrinting = false;

  //SyslogTracerPrintWriter stp;
  SyslogQuietWriter sqw;
  String syslogHost;

  /**
   * Max length in bytes of a message.
   */
  int maxMessageLength = UPPER_MAX_MSG_LENGTH;

    /**
     * If true, the appender will generate the HEADER (timestamp and host name)
     * part of the syslog packet.
     * @since 1.2.15
     */
  private boolean header = false;
    /**
     * Date format used if header = true.
     * @since 1.2.15
     */
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm:ss ", Locale.ENGLISH);
    /**
     * Host name used to identify messages from this appender.
     * @since 1.2.15
     */
  private String localHostname;

    /**
     * Set to true after the header of the layout has been sent or if it has none.
     */
  private boolean layoutHeaderChecked = false;

  public
  SyslogAppender64k() {
    this.initSyslogFacilityStr();
    
  }

  public
  SyslogAppender64k(Layout layout, int syslogFacility) {
    this.layout = layout;
    this.syslogFacility = syslogFacility;
    this.initSyslogFacilityStr();
  }

  public
  SyslogAppender64k(Layout layout, String syslogHost, int syslogFacility) {
    this(layout, syslogFacility);
    setSyslogHost(syslogHost);
  }

  /**
     Release any resources held by this SyslogAppender64k.

     @since 0.8.4
   */
  synchronized
  public
  void close() {
    closed = true;
    if (sqw != null) {
        try {
            if (layoutHeaderChecked && layout != null && layout.getFooter() != null) {
                sendLayoutMessage(layout.getFooter());
            }
            sqw.close();
            sqw = null;
        } catch(java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            sqw = null;
        } catch(IOException e) {
            sqw = null;
        }
    }
  }

  private
  void initSyslogFacilityStr() {
    facilityStr = getFacilityString(this.syslogFacility);

    if (facilityStr == null) {
      System.err.println("\"" + syslogFacility +
                  "\" is an unknown syslog facility. Defaulting to \"USER\".");
      this.syslogFacility = LOG_USER;
      facilityStr = "user:";
    } else {
      facilityStr += ":";
    }
    
  }

  /**
     Returns the specified syslog facility as a lower-case String,
     e.g. "kern", "user", etc.
  */
  public
  static
  String getFacilityString(int syslogFacility) {
    switch(syslogFacility) {
    case LOG_KERN:      return "kern";
    case LOG_USER:      return "user";
    case LOG_MAIL:      return "mail";
    case LOG_DAEMON:    return "daemon";
    case LOG_AUTH:      return "auth";
    case LOG_SYSLOG:    return "syslog";
    case LOG_LPR:       return "lpr";
    case LOG_NEWS:      return "news";
    case LOG_UUCP:      return "uucp";
    case LOG_CRON:      return "cron";
    case LOG_AUTHPRIV:  return "authpriv";
    case LOG_FTP:       return "ftp";
    case LOG_LOCAL0:    return "local0";
    case LOG_LOCAL1:    return "local1";
    case LOG_LOCAL2:    return "local2";
    case LOG_LOCAL3:    return "local3";
    case LOG_LOCAL4:    return "local4";
    case LOG_LOCAL5:    return "local5";
    case LOG_LOCAL6:    return "local6";
    case LOG_LOCAL7:    return "local7";
    default:            return null;
    }
  }

  /**
     Returns the integer value corresponding to the named syslog
     facility, or -1 if it couldn't be recognized.

     @param facilityName one of the strings KERN, USER, MAIL, DAEMON,
            AUTH, SYSLOG, LPR, NEWS, UUCP, CRON, AUTHPRIV, FTP, LOCAL0,
            LOCAL1, LOCAL2, LOCAL3, LOCAL4, LOCAL5, LOCAL6, LOCAL7.
            The matching is case-insensitive.

     @since 1.1
  */
  public
  static
  int getFacility(String facilityName) {
    if(facilityName != null) {
      facilityName = facilityName.trim();
    }
    if("KERN".equalsIgnoreCase(facilityName)) {
      return LOG_KERN;
    } else if("USER".equalsIgnoreCase(facilityName)) {
      return LOG_USER;
    } else if("MAIL".equalsIgnoreCase(facilityName)) {
      return LOG_MAIL;
    } else if("DAEMON".equalsIgnoreCase(facilityName)) {
      return LOG_DAEMON;
    } else if("AUTH".equalsIgnoreCase(facilityName)) {
      return LOG_AUTH;
    } else if("SYSLOG".equalsIgnoreCase(facilityName)) {
      return LOG_SYSLOG;
    } else if("LPR".equalsIgnoreCase(facilityName)) {
      return LOG_LPR;
    } else if("NEWS".equalsIgnoreCase(facilityName)) {
      return LOG_NEWS;
    } else if("UUCP".equalsIgnoreCase(facilityName)) {
      return LOG_UUCP;
    } else if("CRON".equalsIgnoreCase(facilityName)) {
      return LOG_CRON;
    } else if("AUTHPRIV".equalsIgnoreCase(facilityName)) {
      return LOG_AUTHPRIV;
    } else if("FTP".equalsIgnoreCase(facilityName)) {
      return LOG_FTP;
    } else if("LOCAL0".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL0;
    } else if("LOCAL1".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL1;
    } else if("LOCAL2".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL2;
    } else if("LOCAL3".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL3;
    } else if("LOCAL4".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL4;
    } else if("LOCAL5".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL5;
    } else if("LOCAL6".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL6;
    } else if("LOCAL7".equalsIgnoreCase(facilityName)) {
      return LOG_LOCAL7;
    } else {
      return -1;
    }
  }


  private void splitPacket(final String header, final String packet) {
      int byteCount = packet.getBytes().length;

      // If packet is less than limit, then write it.
      // Else, write in chunks.
      if (byteCount <= maxMessageLength) {
          sqw.write(packet);
      } else {
          int split = header.length() + (packet.length() - header.length())/2;
          splitPacket(header, packet.substring(0, split) + "...");
          splitPacket(header, header + "..." + packet.substring(split));
      }      
  }

  public
  void append(LoggingEvent event) {

    if(!isAsSevereAsThreshold(event.getLevel()))
      return;

    // We must not attempt to append if sqw is null.
    if(sqw == null) {
      errorHandler.error("No syslog host is set for SyslogAppedender named \""+
			this.name+"\".");
      return;
    }

    if (!layoutHeaderChecked) {
        if (layout != null && layout.getHeader() != null) {
            sendLayoutMessage(layout.getHeader());
        }
        layoutHeaderChecked = true;
    }

    String hdr = getPacketHeader(event.timeStamp);
    String packet;
    if (layout == null) {
        packet = String.valueOf(event.getMessage());
    } else {
        packet = layout.format(event);
    }
    if(facilityPrinting || hdr.length() > 0) {
        StringBuffer buf = new StringBuffer(hdr);
        if(facilityPrinting) {
            buf.append(facilityStr);
        }
        buf.append(packet);
        packet = buf.toString();
    }

    sqw.setLevel(event.getLevel().getSyslogEquivalent());
    //
    //   if message has a remote likelihood of exceeding 1024 bytes
    //      when encoded, consider splitting message into multiple packets
    if (packet.length() > 256) {
        splitPacket(hdr, packet);
    } else {
        sqw.write(packet);
    }

    if (layout == null || layout.ignoresThrowable()) {
      String[] s = event.getThrowableStrRep();
      if (s != null) {
    	  
    	  
    	  
    	  
        for(int i = 0; i < s.length; i++) {
            if (s[i].startsWith("\t")) {
               sqw.write(hdr+TAB+s[i].substring(1));
            } else {
               sqw.write(hdr+s[i]);
            }
        }
      }
    }
  }

  /**
     This method returns immediately as options are activated when they
     are set.
  */
  public
  void activateOptions() {
      if (header) {
        getLocalHostname();
      }
      if (layout != null && layout.getHeader() != null) {
          sendLayoutMessage(layout.getHeader());
      }
      layoutHeaderChecked = true;
  }

  /**
     The SyslogAppender64k requires a layout. Hence, this method returns
     <code>true</code>.

     @since 0.8.4 */
  public
  boolean requiresLayout() {
    return true;
  }

  /**
    The <b>SyslogHost</b> option is the name of the the syslog host
    where log output should go.  A non-default port can be specified by
    appending a colon and port number to a host name,
    an IPv4 address or an IPv6 address enclosed in square brackets.

    <b>WARNING</b> If the SyslogHost is not set, then this appender
    will fail.
   */
  public
  void setSyslogHost(final String syslogHost) {
    this.sqw = new SyslogQuietWriter(new SyslogWriter64k(syslogHost),
				     syslogFacility, errorHandler);
    //this.stp = new SyslogTracerPrintWriter(sqw);
    this.syslogHost = syslogHost;
  }

  /**
     Returns the value of the <b>SyslogHost</b> option.
   */
  public
  String getSyslogHost() {
    return syslogHost;
  }

  /**
     Set the syslog facility. This is the <b>Facility</b> option.

     <p>The <code>facilityName</code> parameter must be one of the
     strings KERN, USER, MAIL, DAEMON, AUTH, SYSLOG, LPR, NEWS, UUCP,
     CRON, AUTHPRIV, FTP, LOCAL0, LOCAL1, LOCAL2, LOCAL3, LOCAL4,
     LOCAL5, LOCAL6, LOCAL7. Case is unimportant.

     @since 0.8.1 */
  public
  void setFacility(String facilityName) {
    if(facilityName == null)
      return;

    syslogFacility = getFacility(facilityName);
    if (syslogFacility == -1) {
      System.err.println("["+facilityName +
                  "] is an unknown syslog facility. Defaulting to [USER].");
      syslogFacility = LOG_USER;
    }

    this.initSyslogFacilityStr();

    // If there is already a sqw, make it use the new facility.
    if(sqw != null) {
      sqw.setSyslogFacility(this.syslogFacility);
    }
  }

  /**
     Returns the value of the <b>Facility</b> option.
   */
  public
  String getFacility() {
    return getFacilityString(syslogFacility);
  }

  /**
    If the <b>FacilityPrinting</b> option is set to true, the printed
    message will include the facility name of the application. It is
    <em>false</em> by default.
   */
  public
  void setFacilityPrinting(boolean on) {
    facilityPrinting = on;
  }

  /**
     Returns the value of the <b>FacilityPrinting</b> option.
   */
  public
  boolean getFacilityPrinting() {
    return facilityPrinting;
  }

  /**
   * If true, the appender will generate the HEADER part (that is, timestamp and host name)
   * of the syslog packet.  Default value is false for compatibility with existing behavior,
   * however should be true unless there is a specific justification.
   * @since 1.2.15
  */
  public final boolean getHeader() {
      return header;
  }

    /**
     * Returns whether the appender produces the HEADER part (that is, timestamp and host name)
     * of the syslog packet.
     * @since 1.2.15
    */
  public final void setHeader(final boolean val) {
      header = val;
  }

    /**
     * Get the host name used to identify this appender.
     * @return local host name
     * @since 1.2.15
     */
  private String getLocalHostname() {
      if (localHostname == null) {
          try {
            InetAddress addr = InetAddress.getLocalHost();
            localHostname = addr.getHostName();
          } catch (UnknownHostException uhe) {
            localHostname = "UNKNOWN_HOST";
          }
      }
      return localHostname;
  }

    /**
     * Gets HEADER portion of packet.
     * @param timeStamp number of milliseconds after the standard base time.
     * @return HEADER portion of packet, will be zero-length string if header is false.
     * @since 1.2.15
     */
  private String getPacketHeader(final long timeStamp) {
      if (header) {
        StringBuffer buf = new StringBuffer(dateFormat.format(new Date(timeStamp)));
        //  RFC 3164 says leading space, not leading zero on days 1-9
        if (buf.charAt(4) == '0') {
          buf.setCharAt(4, ' ');
        }
        buf.append(getLocalHostname());
        buf.append(' ');
        return buf.toString();
      }
      return "";
  }

    /**
     * Set header or footer of layout.
     * @param msg message body, may not be null.
     */
  private void sendLayoutMessage(final String msg) {
      if (sqw != null) {
          String packet = msg;
          String hdr = getPacketHeader(new Date().getTime());
          if(facilityPrinting || hdr.length() > 0) {
              StringBuffer buf = new StringBuffer(hdr);
              if(facilityPrinting) {
                  buf.append(facilityStr);
              }
              buf.append(msg);
              packet = buf.toString();
          }
          sqw.setLevel(6);
          sqw.write(packet);
      }
  }

  /**
   * @return  The max message length in bytes.
   */
  public int getMaxMessageLength() {
    return maxMessageLength;
  }

  /**
   * @param byteLen  The max message length in bytes.
   */
  public void setMaxMessageLength(int len) {
    if (len < LOWER_MAX_MSG_LENGTH || len > UPPER_MAX_MSG_LENGTH) {
      System.err.println(len 
          + " is an invalid message length. Defaulting to " 
          + UPPER_MAX_MSG_LENGTH + ".");
      len = UPPER_MAX_MSG_LENGTH;
    }
    maxMessageLength = len;
  }
}
