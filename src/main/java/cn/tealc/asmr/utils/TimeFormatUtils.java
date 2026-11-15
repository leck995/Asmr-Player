package cn.tealc.asmr.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 播放时长与时间文本的格式化工具。
 * <p>
 * 时钟格式化只输出 {@code 分:秒}，分钟位可超过 59（不单独进位到小时）。
 *
 * @author Leck
 */
public class TimeFormatUtils {

	private static final String TIME_TEMPLATE="%02d:%02d";
	private static final DateTimeFormatter DATABASE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	/**
	 * 将秒数格式化为 {@code 分:秒}（{@code mm:ss}）时钟文本。
	 *
	 * @param second 时长，单位秒；小数部分被截断（向零取整）
	 * @return {@code mm:ss} 文本；分钟位可超过 59（不进位到小时）
	 */
	public static String formatToClock(double second) {
		return String.format(TIME_TEMPLATE,(int)second/60,(int)second%60);
	}

	/**
	 * 将整数秒格式化为 {@code 分:秒}（{@code mm:ss}）时钟文本。
	 *
	 * @param second 时长，单位秒
	 * @return {@code mm:ss} 文本；分钟位可超过 59（不进位到小时）
	 */
	public static String formatToClock(int second) {
		return String.format(TIME_TEMPLATE,(int)second/60,(int)second%60);
	}



	/**
	 * 将毫秒时长格式化为 {@code 分:秒}（{@code mm:ss}）时钟文本。
	 * <p>
	 * 先除以 1000 换算成秒再截断取整，因此不足 1 秒的时长一律显示为 {@code 00:00}。
	 *
	 * @param mills 时长，单位毫秒
	 * @return {@code mm:ss} 文本；分钟位可超过 59（不进位到小时）
	 */
	public static String formatToClockFromMills(double mills) {
		mills=mills/1000;
		return String.format(TIME_TEMPLATE,(int)mills/60,(int)mills%60);
	}

	/**
	 * 将数据库中的时间文本格式化为 {@code yyyy-MM-dd HH:mm}。
	 * <p>
	 * 兼容 SQLite 的 datetime 文本与 ISO 本地时间；{@code value} 为 null 或空白时返回 {@code "未知"}，
	 * 两种格式都无法解析时原样返回。
	 *
	 * @param value 数据库时间文本，可为 null
	 * @return 格式化后的日期时间，或占位文本 {@code "未知"}/原始文本
	 */
	public static String formatDateTime(String value) {
		if (value == null || value.isBlank()) {
			return "未知";
		}
		try {
			return DISPLAY_DATE_TIME.format(LocalDateTime.parse(value, DATABASE_DATE_TIME));
		} catch (DateTimeParseException ignored) {
			try {
				return DISPLAY_DATE_TIME.format(LocalDateTime.parse(value));
			} catch (DateTimeParseException ignoredAgain) {
				return value;
			}
		}
	}
}
