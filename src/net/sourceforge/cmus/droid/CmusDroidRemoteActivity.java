package net.sourceforge.cmus.droid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * This code is so great, it hurts!!
 * Please clean me!!
 * 
 * @author bboudreau
 * 
 */
public class CmusDroidRemoteActivity extends Activity {
	public enum CmusCommand {
		REPEAT("Repeat", "toggle repeat"),
		SHUFFLE("Shuffle", "toggle shuffle"),
		STOP("Stop", "player-stop"),
		NEXT("Next", "player-next"),
		PREV("Previous", "player-prev"),
		PLAY("Play", "player-play"),
		PAUSE("Pause", "player-pause"),
		// FILE("player-play %s");
		// VOLUME("vol %s"),
		VOLUME_MUTE("Mute", "vol -100%"),
		VOLUME_UP("Volume +", "vol +10%"),
		VOLUME_DOWN("Volume -", "vol -10%"),
		// SEEK("seek %s"),
		STATUS("Status", "status");

		private final String label;
		private final String command;

		private CmusCommand(String label, String command) {
			this.label = label;
			this.command = command;
		}

		public String getCommand() {
			return command;
		}

		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return getLabel();
		}
	}

	/**
	 * 
	 * <pre>
	 *  status playing
	 *  file /home/me/Music/Queen . Greatest Hits I, II, III The Platinum Collection/Queen/Queen - Greatest Hits III (1999)(The Platinum Collection)/11 - Let me Live.mp3
	 *  duration 285
	 *  position 186
	 * tag artist Queen
	 * tag album Greatest Hits, Vol. 3
	 * tag title Let Me Live
	 * tag date 2000
	 * tag genre Rock
	 * tag tracknumber 11
	 * tag albumartist Queen
	 * set aaa_mode all
	 * set continue true
	 * set play_library true
	 * set play_sorted false
	 * set replaygain disabled
	 * set replaygain_limit true
	 * set replaygain_preamp 6.000000
	 * set repeat true
	 * set repeat_current false
	 * set shuffle true
	 * set softvol false
	 * set vol_left 69
	 * set vol_right 69
	 * </pre>
	 * 
	 * @author bboudreau
	 * 
	 */
	public class CmusStatus {

		private String status;
		private String file;
		private String duration;
		private String position;
		private Map<String, String> tags;
		private Map<String, String> settings;

		public CmusStatus() {
			this.tags = new HashMap<String, String>();
			this.settings = new HashMap<String, String>();
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getFile() {
			return file;
		}

		public void setFile(String file) {
			this.file = file;
		}

		public String getDuration() {
			return duration;
		}

		public void setDuration(String duration) {
			this.duration = duration;
		}

		public String getPosition() {
			return position;
		}

		public String getPositionPercent() {
			if (position == null || duration == null) {
				return "Unknown";
			}
			try {
				DecimalFormat twoDForm = new DecimalFormat("#.##%");
				Float positionF = Float.parseFloat(position);
				Float durationF = Float.parseFloat(duration);
				return twoDForm.format(positionF / durationF);
			} catch (Exception e) {
				Log.w(TAG, e);
				return "Unknown";
			}
		}

		public void setPosition(String position) {
			this.position = position;
		}

		public String getTag(String key) {
			String value = tags.get(key);
			return value != null ? value : "Unknown";
		}

		public void setTag(String key, String value) {
			if (this.tags == null) {
				this.tags = new HashMap<String, String>();
			}
			this.tags.put(key, value);
		}

		public String getSettings(String key) {
			String value = settings.get(key);
			return value != null ? value : "Unknown";
		}

		public void setSetting(String key, String value) {
			if (this.settings == null) {
				this.settings = new HashMap<String, String>();
			}
			this.settings.put(key, value);
		}

		public String getUnifiedVolume() {
			String volRight = settings.get("vol_right");
			String volLeft = settings.get("vol_left");
			if (volLeft == null && volRight != null) {
				return volRight + "%";
			} else if (volLeft != null && volRight == null) {
				return volLeft + "%";
			}
			try {
				Float volRightF = Float.parseFloat(volRight);
				Float volLeftF = Float.parseFloat(volLeft);

				DecimalFormat twoDForm = new DecimalFormat("#.##");
				return twoDForm.format((volRightF + volLeftF) / 2.0f) + "%";
			} catch (Exception e) {
				Log.w(TAG, e);
				return "Unknown";
			}
		}

		public String toSimpleString() {
			StringBuilder strBuilder = new StringBuilder();
			strBuilder.append("Artist: ").append(getTag("artist")).append("\n");
			strBuilder.append("Title: ").append(getTag("title")).append("\n");
			strBuilder.append("Position: ").append(getPositionPercent()).append("\n");
			strBuilder.append("Volume: ").append(getUnifiedVolume()).append("\n");
			return strBuilder.toString();
		}
	}

	public static final String TAG = "CmusDroidRemoteActivity";

	private AutoCompleteTextView mHostText;
	private EditText mPortText;
	private EditText mPasswordText;
	private Spinner mCommandSpinner;
	private Button mSendCommandButton;
	ArrayAdapter<String> hostAdapter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Obtain handles to UI objects
		mHostText = (AutoCompleteTextView) findViewById(R.id.hostText);
		mPortText = (EditText) findViewById(R.id.portText);
		mPasswordText = (EditText) findViewById(R.id.passwordText);
		mCommandSpinner = (Spinner) findViewById(R.id.commandSpinner);
		mSendCommandButton = (Button) findViewById(R.id.sendCommandButton);

		mPortText.setText("3000");

		hostAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line,
				new ArrayList<String>());

		mHostText.setAdapter(hostAdapter);

		runSearchHosts();

		mCommandSpinner.setAdapter(new ArrayAdapter<CmusCommand>(this,
				android.R.layout.simple_spinner_item, CmusCommand.values()));

		mSendCommandButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommandClicked();
			}
		});
	}

	private void runSearchHosts() {

		if (isUsingWifi()) {

			new Thread(new Runnable() {

				public void run() {
					try {
						InetAddress localhost = null;
						;
						for (final Enumeration<NetworkInterface> interfaces = NetworkInterface
								.getNetworkInterfaces(); interfaces
								.hasMoreElements() && localhost == null;) {
							final NetworkInterface cur = interfaces.nextElement();

							if (cur.getName().equals("lo")) {
								continue;
							}
							Log.v(TAG, "interface " + cur.getName());

							for (final Enumeration<InetAddress> inetAddresses = cur
									.getInetAddresses(); inetAddresses
									.hasMoreElements() && localhost == null;) {
								final InetAddress inet_addr = inetAddresses
										.nextElement();

								if (!(inet_addr instanceof Inet4Address)) {
									continue;
								}

								Log.v(TAG, "Found local addr: " + inet_addr);
								localhost = inet_addr;
							}
						}

						// this code assumes IPv4 is used

						if (localhost != null) {

							byte[] ip = localhost.getAddress();

							for (int i = 1; i <= 254; i++) {

								ip[3] = (byte) i;
								final InetAddress address = InetAddress.getByAddress(ip);

								if (address.isReachable(200)) {
									Log.v(TAG, "Found an addr on LAN: "
											+ address.getHostAddress());
									CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
										public void run() {
											hostAdapter.add(address.getHostAddress());
										}
									});
									// machine is turned on and can be pinged
								} else if (!address.getHostAddress().equals(
										address.getHostName())) {
									// machine is known in a DNS lookup
								} else {
									// the host address and host name are equal,
									// meaning
									// the
									// host
									// name could not be resolved
								}
							}

						}
					} catch (Exception e) {
						Log.e(TAG, "Error: " + e.getMessage(), e);
					}
				}
			}).start();
		}
	}

	private void onSendCommandClicked() {
		Log.v(TAG, "Save button clicked");
		if (validate()) {
			if (isUsingWifi()) {
				sendCommand(mHostText.getText().toString(),
						Integer.parseInt(mPortText.getText().toString()),
						mPasswordText.getText().toString(),
						(CmusCommand) mCommandSpinner.getSelectedItem());
			} else {
				alert("Could not send command", "Not sending command: not on Wifi.");
			}
		}
		// finish();
	}

	private void alert(String title, String message) {
		Log.v(TAG, message);
		new AlertDialog.Builder(this)
				.setMessage(message)
				.setTitle(title).show();
	}

	private boolean validate() {
		boolean valid = true;

		if (!Validator.validateString(mHostText.getText().toString())) {
			valid = false;
			mHostText.setError("the hostname is not valid");
		} else {
			mHostText.setError(null);
		}

		if (!Validator.validateInteger(mPortText.getText().toString())) {
			valid = false;
			mPortText.setError("the port is not valid");
		} else {
			mPortText.setError(null);
		}

		if (!Validator.validateString(mPasswordText.getText().toString())) {
			valid = false;
			mPasswordText.setError("the password is not valid");
		} else {
			mPasswordText.setError(null);
		}

		if (!valid) {
			alert("Could not send command", "Not sending command, some parameters are invalid.");
		}

		return valid;
	}

	private void addTagOrSetting(CmusStatus cmusStatus, String line) {
		int firstSpace = line.indexOf(' ');
		int secondSpace = line.indexOf(' ', firstSpace + 1);
		String type = line.substring(0, firstSpace);
		String key = line.substring(firstSpace + 1, secondSpace);
		String value = line.substring(secondSpace + 1);
		if (type.equals("set")) {
			cmusStatus.setSetting(key, value);
		} else if (type.equals("tag")) {
			cmusStatus.setTag(key, value);
		} else {
			Log.e(TAG, "Unknown type in status: " + line);
		}
	}

	private void handleStatus(String status) {

		CmusStatus cmusStatus = new CmusStatus();

		String[] strs = status.split("\n");

		for (String str : strs) {
			if (str.startsWith("set") || str.startsWith("tag")) {
				addTagOrSetting(cmusStatus, str);
			} else {
				int firstSpace = str.indexOf(' ');
				String type = str.substring(0, firstSpace);
				String value = str.substring(firstSpace + 1);
				if (type.equals("status")) {
					cmusStatus.setStatus(value);
				} else if (type.equals("file")) {
					cmusStatus.setFile(value);
				} else if (type.equals("duration")) {
					cmusStatus.setDuration(value);
				} else if (type.equals("position")) {
					cmusStatus.setPosition(value);
				}
			}
		}

		alert("Received Status", cmusStatus.toSimpleString());
	}

	private void sendCommand(final String host, final int port,
			final String password, final CmusCommand command) {

		new Thread(new Runnable() {
			private String readAnswer(BufferedReader in) throws IOException {
				StringBuilder answerBuilder = new StringBuilder();

				String line;
				while ((line = in.readLine()) != null && line.length() != 0) {
					answerBuilder.append(line).append("\n");
				}

				return answerBuilder.toString();
			}

			private void handleCmdAnswer(BufferedReader in, final CmusCommand command) throws Exception {
				final String cmdAnswer = readAnswer(in);
				if (cmdAnswer != null && cmdAnswer.trim().length() != 0) {
					Log.v(TAG, "Received answer to " + command.getLabel() + ": "
							+ cmdAnswer.replaceAll("\n", "\n\t").replaceFirst("\n\t", "\n"));
					CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							if (command.equals(CmusCommand.STATUS)) {
								handleStatus(cmdAnswer);
							} else {
								alert("Message from Cmus", "Received message: " + cmdAnswer);
							}
						}
					});
				}
			}

			private void validAuth(BufferedReader in) throws Exception {
				String passAnswer = readAnswer(in);
				if (passAnswer != null && passAnswer.trim().length() != 0) {
					throw new Exception("Could not login: " + passAnswer);
				}
			}

			public void run() {
				Socket socket = null;
				BufferedReader in = null;
				PrintWriter out = null;
				try {
					socket = new Socket(host, port);
					Log.v(TAG, "Connected to " + host + ":" + port);
					in = new BufferedReader(new InputStreamReader(socket.getInputStream()), Character.SIZE);
					out = new PrintWriter(socket.getOutputStream(), true);

					out.println("passwd " + password);
					validAuth(in);
					out.println(command.getCommand());
					handleCmdAnswer(in, command);
				} catch (final Exception e) {
					Log.e(TAG, "Could not send the command", e);
					CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							alert("Could not send command", "Could not send the command: "
									+ e.getLocalizedMessage());
						}
					});
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (Exception e1) {
						}
						in = null;
					}
					if (out != null) {
						try {
							out.close();
						} catch (Exception e1) {
						}
						out = null;
					}
					if (socket != null) {
						try {
							socket.close();
						} catch (Exception e) {
						}
						socket = null;
					}
				}
			}
		}).start();
	}

	private boolean isUsingWifi() {
		if ("sdk".equals(Build.PRODUCT)) {
			Log.v(TAG, "Executing on emulator");
			return true;
		}
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		return mWifi.isConnected();
	}

}