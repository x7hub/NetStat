package edu.bupt.netstat.analyze;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.jnetpcap.Pcap;
import org.jnetpcap.packet.JPacket;
import org.jnetpcap.packet.JPacketHandler;
import org.jnetpcap.packet.format.FormatUtils;
import org.jnetpcap.protocol.network.Ip4;
import org.jnetpcap.protocol.network.Ip6;
import org.jnetpcap.protocol.tcpip.Http;
import org.jnetpcap.protocol.tcpip.Tcp;
import org.jnetpcap.protocol.tcpip.Udp;
import org.jnetpcap.protocol.tcpip.Http.Request;

import android.util.Log;

/**
 * PacketReader
 * 
 * @author zzz
 * 
 */
public class PacketReader {
	private final static String TAG = "PacketReader";

	private static PacketReader instance;

	public ArrayList<JPacket> packets;

	public static String localIP;
	public String pcapFileName;

	public boolean[] retTable;
	public HashMap<Integer, Integer> rttMap;
	public long pktTime;
	public float pktLoss;
	public int avrRtt;
	public int avrDns;
	public int avrRes;
	public int avrTime;
	public long avrSpeed;
	public float delayJitter;
	public long traffic;
	public int threadNum;
	public int advertise_num;
	public int advertise_traffic;
	public float res_efficiency;
	public float ssl;
	public float tradeTime = 0;
	private  static int ssl_num = 0;

	public HashMap<String, Integer> responseTime = new HashMap<String, Integer>();
	public HashMap<String, Integer> dnsTime = new HashMap<String, Integer>();
	public HashMap<Integer, Integer> rrMap = new HashMap<Integer, Integer>();
	ArrayList<Integer> serverport = new ArrayList<Integer>();

	private String[] ipList;
	
	private static Ip4 ip4 = new Ip4();
	private static Ip6 ip6 = new Ip6();
	private static Tcp tcp = new Tcp();
	private static Udp udp = new Udp();
	private static Http http = new Http();
	
	static {
		System.loadLibrary("jnetpcap");
	}

	/**
	 * @author zzz
	 * 
	 */
	public PacketReader() {
	}

	public PacketReader(String[] ipList, String localIP, String pcapFileName) {
		super();
		this.ipList = ipList;
		if(ipList != null){
			for(String s: ipList){
				Log.i("iplist", " " + s);
			}
		}
		this.localIP = localIP;
		this.pcapFileName = pcapFileName;
		packets = new ArrayList<JPacket>();
		listPackets();
	}
	
	/**
	 * @author zzz
	 * 
	 */
	public static PacketReader getInstance() {
		if (instance == null) {
//			synchronized (PacketReader.class) {
//				if (instance == null) {
					instance = new PacketReader();
//				}
//			}
		}
		return instance;
	}

	/**
	 * @author zzz
	 * 
	 */
	public void read(String localIP, String pcapFileName,
			OnReadComplete onReadComplete, int pkgType) {
		//this.localIP = localIP;
		//this.pcapFileName = pcapFileName;

		//packets = new ArrayList<JPacket>();
		//listPackets();
		retTable = new boolean[packets.size()];
		rttMap = new HashMap<Integer, Integer>();

		pktTime = getPktTime();//
		traffic = new File(pcapFileName).length();

		switch (pkgType) {
		case ScoreStatisticsSuper.WEB:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延
			avrRes = getHttpResponse();// 网页响应时延
			avrTime = getAvrTime();// 网页下载时延以及下载文件时延
			avrSpeed = getAvrSpeed();
			pktLoss = getRetTimes();// 丢包率
			break;
		case ScoreStatisticsSuper.DOWNLOAD:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延
			avrTime = getAvrTime();// 网页下载时延以及下载文件时延
			threadNum = getThreadNum();// 获取进程数
			avrSpeed = getAvrSpeed();
			pktLoss = getRetTimes();// 丢包率
			break;
		case ScoreStatisticsSuper.VIDEO:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延
			avrRes = getHttpResponse();// 网页响应时延
			delayJitter = getDelayJitter();
			avrSpeed = getAvrSpeed();
			pktLoss = getRetTimes();// 丢包率
			break;
		case ScoreStatisticsSuper.GAME:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延
			avrRes = getHttpResponse();// 网页响应时延
			avrSpeed = getAvrSpeed();
			pktLoss = getRetTimes();// 丢包率
			advertise_traffic = getAdvertisement();// 获取广告流量
			advertise_num = getAdvertiseNum();
			res_efficiency = getResrcEfficiency();
			break;
		case ScoreStatisticsSuper.TRADE:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延
			delayJitter = getDelayJitter();
			pktLoss = getRetTimes();// 丢包率
			traffic = new File(pcapFileName).length();
			ssl = getSSL();// 安全系数
			tradeTime = getTradeTime();
			break;
		case ScoreStatisticsSuper.SOCIAL:
			avrDns = getDns();// dns时延
			avrRtt = getRtt();// tcp连接时延，网页连接时延,发送时延
			pktLoss = getRetTimes();// 丢包率
			traffic = new File(pcapFileName).length();
			break;
		default:
		}
		testLog();
		onReadComplete.onComplete();
	}

	/**
	 * @author xiang
	 * 
	 */
	private void listPackets() {
		StringBuilder errbuf = new StringBuilder();
		Pcap pcap = Pcap.openOffline(pcapFileName, errbuf);
		JPacketHandler<String> handler = new JPacketHandler<String>() {

			@Override
			public void nextPacket(JPacket packet, String user) {
				if (filterByIp(packet, ipList)){
					packets.add(packet);
				}
			}
		};
		try {
			pcap.loop(-1, handler, null);
		} finally {
			pcap.close();
		}
	}

	/**
	 * @author xiang
	 * 
	 */
	// dpt > 1024 && spt > 1024 : compare with local ip 
		private static String getServerIp(JPacket pkt) { 
			String ip = "";
			byte[] addr;
			if (pkt.hasHeader(Tcp.ID)) {
				pkt.getHeader(tcp);
				if (pkt.hasHeader(Ip4.ID)) {
					pkt.getHeader(ip4);
//					addr = (tcp.destination() < 1024) ? ip4.destination() : ip4.source();
//					ip = FormatUtils.ip(addr);
					ip = FormatUtils.ip(ip4.source());
					if (ip.equals(localIP)) {
						ip = FormatUtils.ip(ip4.destination());	
					}
				} else if (pkt.hasHeader(Ip6.ID)) {
					pkt.getHeader(ip6);
					addr = (tcp.destination() < 1024) ? ip6.destination() : ip6.source();
					ip = FormatUtils.asStringIp6(addr, true);
				}
			} else if (pkt.hasHeader(Udp.ID)) {
				pkt.getHeader(udp);
				if (pkt.hasHeader(Ip4.ID)) {
					pkt.getHeader(ip4);
//					int dpt = udp.destination();
//					// SSDP address: 239.255.255.250:1900
//					addr = (dpt > 1024 || dpt == 1900) ? ip4.destination() : ip4.source();
//					ip = FormatUtils.ip(addr);
					ip = FormatUtils.ip(ip4.source());
					if (ip.equals(localIP)) {
						ip = FormatUtils.ip(ip4.destination());	
					}
				} else if (pkt.hasHeader(Ip6.ID)) {
					pkt.getHeader(ip6);
					addr = (udp.destination() > 1024) ? ip6.destination() : ip6.source();
					ip = FormatUtils.asStringIp6(addr, true);
				}
			}
			return ip;
		}

	/**
	 * @author xiang
	 * 
	 */
	private boolean filterByIp(JPacket pkt, String[] ipList) {
		if (this.localIP == null) {
			Log.e("LocalIp", "Local ip is null!");
			return false;
		}
		if (isDnsPkt(pkt)) {
			return true;
		}
		String ip = getServerIp(pkt);
		if (ipList == null || ipList.length == 0 || ip.equals("")) {
			Log.e("Netstat", "Ip list is null or empty! or Remote ip is empty!");
			return false;
		}
		for (String s : ipList) {
			if (ip.equals(s)) {
				Log.i("RemoteIp", ip);
				return true;
			}
		}
		Log.i("OtherIp ", ip);
		return false;
	}

	/**
	 * @author xiang
	 * 
	 */
	private int getRtt() {
		int rtt, totRtt = 0, cnt = 0;
		long ack = 0, seq = 0, t2 = 0;
		JPacket p = null;
		Tcp h = new Tcp();
		int psize = packets.size();
		// The android tool SparseArrays can be used.
		HashMap<Integer, Long[]> map = new HashMap<Integer, Long[]>();
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			if (p.hasHeader(Tcp.ID)) {
				p.getHeader(h);
				if (h.flags() != 0x10) { // not only ACK
					ack = h.ack();
					seq = h.seq();
					t2 = p.getCaptureHeader().timestampInMicros();
					map.put(i, new Long[] { ack, seq, t2 });
				}
				if (h.flags() != 0x02) { // not only SYN
					ack = h.ack();
					seq = h.seq();
					Set<Integer> s = map.keySet();
					for (Integer m : s) {
						if (m.intValue() == i) {
							continue;
						}
						Long[] val = map.get(m);
						if (ack == (val[1] + 1) || seq == val[0]) {
							map.remove(m);
							rtt = (int) (p.getCaptureHeader()
									.timestampInMicros() - val[2]);
							if (rtt < 500000) {
								rttMap.put(i, rtt);
								rrMap.put(m, i);
								totRtt += rtt;
								cnt++;
							}
							break;
						}
					}
				}
			}
		}
		return cnt > 0 ? totRtt / cnt : 0;
	}

	/**
	 * @author xiang
	 * 
	 */
	private float getRetTimes() {
		int cnt = 0;
		int psize = packets.size();
		long seq, pseq;
		String key = "";
		Tcp h = new Tcp();
		JPacket p = null;
		HashMap<String, Long> map = new HashMap<String, Long>();
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			if (p.hasHeader(Tcp.ID)) {
				p.getHeader(h);
				seq = h.seq();
				key = (h.source() > 1023) ? ("C" + h.source()) : ("S" + h
						.destination());
				if (map.containsKey(key)) {
					pseq = map.get(key);
					if (pseq < seq) {
						map.put(key, seq);
					} else if (pseq > seq) {
						cnt++;
						Log.i(key, "" + i);
						retTable[i] = true;
					}
				} else {
					map.put(key, seq);
				}
			}
		}
		return psize > 0 ? ((float) cnt) / psize : 0;
	}

	/**
	 * @author xiang
	 * 
	 */
	private int getDns() {
		long t1 = 0;
		int transId = 0, plOff = 0;
		int offset = 0, size = 0;
		int cnt = 0, totDns = 0;
		int psize = packets.size();
		JPacket p;
		byte[] url = null;
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			if (p.hasHeader(Udp.ID)) {
				Udp udp = new Udp();
				p.getHeader(udp);
				if (udp.destination() == 53) {
					plOff = udp.getPayloadOffset();
					offset = plOff + 12 + 1;
					size = udp.getPayloadLength() - (12 + 4 + 2);
					transId = (p.getByte(plOff) << 8) & 0xFF00
							| p.getByte(plOff + 1) & 0xFF;
					url = p.getByteArray(offset, size);
					t1 = p.getCaptureHeader().timestampInMicros();
				} else if (udp.source() == 53 && transId != 0) {
					int tmpId = (p.getByte(plOff) << 8) & 0xFF00
							| p.getByte(plOff + 1) & 0xFF;
					// if (compByteArray(url,p.getByteArray(offset, size))) {
					if (transId == tmpId) {
						int dns = (int) (p.getCaptureHeader()
								.timestampInMicros() - t1);
						dnsTime.put(new String(url) + "~" + i, dns);
						totDns += dns;
						cnt++;
						transId = 0;
					}
				}
			}
		}
		return cnt > 0 ? totDns / cnt : 0;
	}
	
	/**
	 * @author xiang
	 * @time 2014.08.05
	 * 
	 */
	private static boolean isDnsPkt(JPacket p) {
		if (p != null && p.hasHeader(Udp.ID)) {
			p.getHeader(udp);
			if (udp.destination() == 53 || udp.source() == 53) {
				return true;
			} 
		}
		return false;
	}

	/**
	 * @author xiang
	 * 
	 */
	private boolean compByteArray(byte[] b1, byte[] b2) {
		int i = 0;
		if (b1.length == b2.length) {
			while (b1[i] == b2[i] && i < b1.length - 1) {
				i++;
			}
			return (b1[i] == b2[i]);
		}
		return false;
	}

	/**
	 * @author xiang
	 * 
	 */
	private int getHttpResponse() {
		int totRes = 0, cnt = 0;
		int psize = packets.size();
		JPacket p;
		String s = null;
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			if (p.hasHeader(Http.ID)) {
				Http http = new Http();
				p.getHeader(http);
				s = http.fieldValue(Request.Host);
				Integer res = rrMap.get(i);
				if (s != null && res != null) {
					String get = s + http.fieldValue(Request.RequestUrl);
					int r = rttMap.get(res); // TODO error here
					responseTime.put(get, r);
					Log.v("ThreadNum", get);
					totRes += r;
					cnt++;
				}
			}
		}
		return cnt > 0 ? totRes / cnt : 0;
	}

	/**
	 * @author Yin
	 * 
	 */
	private int getThreadNum() {
		TreeSet<String> ts = new TreeSet<String>();
		int psize = packets.size();
		JPacket p;
		String s = null;
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			if (p.hasHeader(Http.ID)) {
				Http http = new Http();
				p.getHeader(http);
				s = http.fieldValue(Request.Host);
				Integer res = rrMap.get(i);
				if (s != null && res != null) {
					String get = s + http.fieldValue(Request.RequestUrl);
					if (get.contains("mp3") || get.contains("m4a")
							|| get.contains("apk")) {
						ts.add(get);
					}
				}
			}
		}
		return ts.size() == 0 ? 1 : ts.size();
	}

	/**
	 * @author xiang
	 * 
	 */
	private long getPktTime() {
		long pktTime = 0;
		long t1, t2;
		int psize = packets.size();
		if (psize > 0) {
			JPacket p;
			p = packets.get(0);
			t1 = p.getCaptureHeader().timestampInMicros();
			p = packets.get(psize - 1);
			t2 = p.getCaptureHeader().timestampInMicros();
			pktTime = t2 - t1;
		}
		return pktTime;
	}

	/**
	 * @author xiang
	 * 
	 */
	private int getAvrTime() {
		int n = dnsTime.size(); // to represent the num of web accessed.
		n = n > 0 ? n : 1;
		return (int) (pktTime / n);
	}

	/**
	 * @author xiang
	 * 
	 */
	private long getAvrSpeed() {
		return pktTime > 0 ? (1000000 * traffic) / pktTime : 0;
	}

	/**
	 * @author xiang
	 * 
	 */
	private void testLog() {
		for (String s : dnsTime.keySet()) {
			Log.i("DNS", s + " " + dnsTime.get(s));
		}
		for (String s : responseTime.keySet()) {
			Log.i("HttpResponse", s + " " + responseTime.get(s));
		}
		for (int s : rttMap.keySet()) {
			Log.i("RTT " + s, "" + rttMap.get(s));
		}
		Log.i("PktLoss", "" + pktLoss);
		Log.i("AvrDns", "" + avrDns + "us");
		Log.i("AvrRtt", "" + avrRtt + "us");
		Log.i("AvrRes", "" + avrRes + "us");
		Log.i("AvrTime", "" + avrTime + "ms");
		Log.i("AvrSpeed", "" + avrSpeed + "B/s");
		Log.i("Traffic", "" + traffic + "B");
	}

	// public void getTcpHandshake() {
	// ArrayList<Integer> al = new ArrayList<Integer>();
	// for (String s:dnsTime.keySet()) {
	// al.add(Integer.valueOf(s.split("~")[1]));
	// }
	// }

	private float getDelayJitter() {
		float sum = 0;
		int count = 0;
		for (int i = 0; i < packets.size(); i++) {
			if (rttMap.containsKey(i)) {
				sum += Math.sqrt(((float) rttMap.get(i) - avrRtt)
						* (rttMap.get(i) - avrRtt));
				count++;
				Log.v(TAG, "rttMap.get(i) - " + rttMap.get(i));
			}
		}
		Log.v(TAG, "sum / (count - 1) - " + (sum / (count - 1)));
		return (sum / (count - 1));
	}

	/**
	 * @author yuan 需要优化
	 * */
	private int getAdvertisement() {
		int advbytes = 0;
		Http http = new Http();
		Tcp tcp = new Tcp();
		for (int i = 0; i < packets.size(); i++) {
			JPacket p = packets.get(i);
			if (p.hasHeader(Http.ID)) {

				p.getHeader(http);
				String s = http.fieldValue(Request.Host);
				Integer res = rrMap.get(i);
				if (s != null && res != null) {
					String get = s + http.fieldValue(Request.RequestUrl);
					if (get.contains(".jpg") || get.contains(".png")) {
						if (p.hasHeader(Tcp.ID)) {

							p.getHeader(tcp);
							int portnum = tcp.destination();
							if (portnum == 80) {
								portnum = tcp.source();
							}
							if (!serverport.contains(portnum)) {
								serverport.add(Integer.valueOf(portnum));
							}
						}
					}
				}
			}
		}
		Log.i("aaa", "out1");
		for (int i = 0; i < packets.size(); i++) {
			JPacket p = packets.get(i);
			if (p.hasHeader(Tcp.ID)) {
				// Tcp tcp = new Tcp();
				p.getHeader(tcp);
				if (serverport.contains(tcp.destination())
						|| serverport.contains(tcp.source())) {
					advbytes += p.getPacketWirelen();
					Log.i("ren", "Packets size is " + p.getPacketWirelen());
				}
			}
		}
		Log.i("aaa", "out2");
		return advbytes;
	}

	/**
	 * @author yuan get advertisement num
	 * */
	private int getAdvertiseNum() {
		return serverport.size();
	}

	/**
	 * @author yuan
	 * @return return a float num which means num%
	 * 
	 * */
	private float getResrcEfficiency() { // 百分之
		int payloadbytes = 0;
		int totalbytes = 0;
		float effrate;
		Tcp tcp = new Tcp();
		Http http = new Http();
		for (int i = 0; i < packets.size(); i++) {
			JPacket p = packets.get(i);
			if (p.hasHeader(Tcp.ID)) {
				if (p.hasHeader(Http.ID)) {
					p.getHeader(http);
					payloadbytes += http.getPayloadLength();
					Log.d("ren", "payloadbytes is " + payloadbytes);
				} else {
					p.getHeader(tcp);
					payloadbytes += tcp.getPayloadLength();
					Log.d("ren", "payloadbytes is " + payloadbytes);
				}
			} else if (p.hasHeader(Udp.ID)) {

			} else {

			}
			totalbytes += p.getPacketWirelen();
			Log.d("ren", "totalbytes is " + totalbytes);
		}
		Log.i("ren", "payload size is " + payloadbytes + "B.totalsize is "
				+ totalbytes + " B");
		effrate = (float) payloadbytes / (float) totalbytes;
		float a = (float) Math.round(effrate * 10000) / 100;
		Log.i("ren", "effiticv is " + a);
		return a;
	}

	/**
	 * @author xiang 获取交易类的安全性
	 */
	private float getSSL() {
		int cnt = 0;
		int psize = packets.size();
		int pktLen = 0;
		int plOff = 0;
		byte[] id1, id2;
		Tcp h = new Tcp();
		JPacket p = null;
		for (int i = 0; i < psize; i++) {
			p = packets.get(i);
			pktLen = p.size();
			if (p.hasHeader(Tcp.ID)) {
				p.getHeader(h);
				plOff = h.getPayloadOffset();
				int offset = plOff + 1;
				if ((offset + 2) < pktLen) {
					id1 = p.getByteArray(offset, 2);
					id2 = p.getByteArray(pktLen - 8, 2);
					if (((id1[0] << 8) & 0xFF00 | id1[1] & 0xFF) == 0x0301
							|| ((id2[0] << 8) & 0xFF00 | id2[1] & 0xFF) == 0x0301) {
						Log.i("SSL ", " " + (i + 1));
						cnt++;
						ssl_num = i;
						if (rrMap.containsKey(i) || rrMap.containsValue(i)) {
							cnt++;
						}
					}
				}
			}
		}
		return ((float) cnt) / psize;
	}

	/**
	 * @author yyl
	 */
	private float getTradeTime() {
		long t1 = 0,t2 = 0;
		int psize = packets.size();
		JPacket p = null;
		if(ssl_num > 0){
			p = packets.get(ssl_num);
			t1 = p.getCaptureHeader().timestampInMicros();
			p = packets.get(psize - 1);
			t2 = p.getCaptureHeader().timestampInMicros();
			tradeTime = t2 - t1;
		}
		return tradeTime;
	}
}
