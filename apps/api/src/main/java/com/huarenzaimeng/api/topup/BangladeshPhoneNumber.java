package com.huarenzaimeng.api.topup;

final class BangladeshPhoneNumber {
    static String normalize(String raw){if(raw==null)throw new TopupCoordinator.Conflict("RECIPIENT_INVALID");String digits=raw.replaceAll("[^0-9]","");if(digits.startsWith("00880"))digits=digits.substring(2);else if(digits.startsWith("0")&&digits.length()==11)digits="88"+digits;if(!digits.matches("8801[3-9][0-9]{8}"))throw new TopupCoordinator.Conflict("RECIPIENT_INVALID");return digits;}
    private BangladeshPhoneNumber(){}
}
