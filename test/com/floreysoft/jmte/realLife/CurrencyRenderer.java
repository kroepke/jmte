package com.floreysoft.jmte.realLife;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import com.floreysoft.jmte.NamedRenderer;
import com.floreysoft.jmte.RenderFormatInfo;

public class CurrencyRenderer implements NamedRenderer {

	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(
			"##,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
	private static final String EURO_CHARACTER = "\u20AC";

	@Override
	public RenderFormatInfo getFormatInfo() {
		return null;
	}

	@Override
	public String getName() {
		return "currency";
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { BigDecimal.class };
	}

	@Override
	public String render(Object o, String format, Locale locale, Map<String, Object> model) {
		if (o instanceof BigDecimal) {
			final NumberFormat numberFormat;
			if (format == null) {
				numberFormat = DECIMAL_FORMAT;
			} else {
				numberFormat = new DecimalFormat(format, DecimalFormatSymbols
						.getInstance(Locale.GERMANY));
			}
			String formatted = numberFormat.format(o) + " " + EURO_CHARACTER;
			return formatted;
		}
		return null;
	}

}
