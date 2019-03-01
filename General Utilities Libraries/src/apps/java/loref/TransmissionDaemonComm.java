package apps.java.loref;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 24 gen 2019.
 */

// pi@raspberrypi:~ $ sudo transmission-remote -n transmission:transmission -l
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
// 0		 1         2         3         4         5         6         7         8         9         0         1
// 0                                                                                                             1
// ID     Done       Have  ETA           Up    Down  Ratio  Status       Name
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//    6    n/a       None  Unknown      0.0     0.0   None  Idle         Millenium.Uomini.Che.Odiano.Le.Donne.2011.iTALiAN.AC3.BrRip.720p.x264.TrTd_TeaM
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
// Sum:              None               0.0     0.0
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
// 0		 1         2         3         4         5         6         7         8         9         0         1
// 0  

public class TransmissionDaemonComm {

	public class TorrentElement {

		private String id;
		private String done;
		private String have;
		private String eta;
		private double up;
		private double down;
		private String ratio;
		private String status;
		private String name;

		private String rawInput;

		public TorrentElement(String raw) {

			this.id = raw.substring(0, 4).trim();
			this.done = raw.substring(4, 11).trim();
			this.have = raw.substring(11, 22).trim();
			this.eta = raw.substring(22, 31).trim();
			this.up = Double.parseDouble(raw.substring(31, 40).trim());
			this.down = Double.parseDouble(raw.substring(40, 48).trim());

			if (raw.length() > 48) {
				this.ratio = raw.substring(48, 55).trim();
				this.status = raw.substring(57, 70).trim();
				this.name = raw.substring(70).trim();
			}

		}

		public String getId() {
			return this.id;
		}

		public String getDone() {
			return this.done;
		}

		public String getHave() {
			return this.have;
		}

		public String getEta() {
			return this.eta;
		}

		public double getUp() {
			return this.up;
		}

		public double getDown() {
			return this.down;
		}

		public String getRatio() {
			return this.ratio;
		}

		public String getStatus() {
			return this.status;
		}

		public String getName() {
			return this.name;
		}

	}

	private List<TorrentElement> torrentElements;
	private TorrentElement torrentSum;

	public TransmissionDaemonComm(String rawInput) {

		this.torrentElements = new ArrayList<TorrentElement>();

		String[] rawInputLines = rawInput.split("[\n]");

		if (rawInputLines.length >= 3) {

			for (int i = 1; i < rawInputLines.length - 1; i++) {

				this.torrentElements.add(new TorrentElement(rawInputLines[i]));

			}

			this.torrentSum = new TorrentElement(rawInputLines[rawInputLines.length - 1]);

		}

	}

	public int getTorrentsCount() {

		return this.torrentElements.size();

	}

	public TorrentElement getTorrent(int index) {
		return this.torrentElements.get(index);
	}
	
	public String getJSONData(){
		
		JSONObject result = new JSONObject();
		JSONObject torrentData = new JSONObject();
		
		for(TorrentElement t: this.torrentElements){
			
			JSONObject element = new JSONObject();
			
			element.put("ID", t.getId());
			element.put("Done", t.getDone());
			element.put("Have", t.getHave());
			element.put("ETA", t.getEta());
			element.put("Up", t.getUp());
			element.put("Down", t.getDown());
			element.put("Ratio", t.getRatio());
			element.put("Status", t.getStatus());
			element.put("Name", t.getName());
			
			torrentData.append("Torrents",element);
			
		}
		
		result.put("TorrentData", torrentData);
		
		return result.toString();
	}

}
