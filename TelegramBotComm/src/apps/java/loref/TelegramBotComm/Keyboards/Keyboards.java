package apps.java.loref.TelegramBotComm.Keyboards;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import static apps.java.loref.LogUtilities.*;

/**
 * TODO Put here a description of what this class does.
 *
 * @author lore_f. Created 23 apr 2019.
 */
@SuppressWarnings("javadoc")
public class Keyboards {

	public static InlineKeyboardMarkup createKeyboard(List<List<InlineKeyboardButton>> keys) {

		InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
		markupInline.setKeyboard(keys);
		return markupInline;

	}

	public static InlineKeyboardMarkup createKeyboard(JSONObject root) {

		InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();

		try {

			if (root.has("lines")) {

				JSONArray lines = root.getJSONArray("lines");

				for (int i = 0; i < lines.length(); i++) {

					List<InlineKeyboardButton> keyboardLine = new ArrayList<InlineKeyboardButton>();

					JSONObject line = lines.getJSONObject(i);

					if (line.has("buttons")) {

						JSONArray buttons = line.getJSONArray("buttons");

						for (int j = 0; j < buttons.length(); j++) {

							JSONObject button = buttons.getJSONObject(j);
							keyboardLine.add(new InlineKeyboardButton().setText(button.getString("label"))
									.setCallbackData(button.getString("action")));

						}

						markupInline.getKeyboard().add(keyboardLine);

					}

				}

			}

		} catch (JSONException e) {

			exceptionLog_REDXTERM(Keyboards.class, e);

		}

		return markupInline;
	}

	public static InlineKeyboardMarkup createKeyboard(String code) {

		try {

			JSONObject json = new JSONObject(code);
			return createKeyboard(json);
			
		} catch (JSONException e) {

			exceptionLog_REDXTERM(Keyboards.class, e);
			return null;
			
		}

	}

}
