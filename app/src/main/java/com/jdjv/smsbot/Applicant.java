package com.jdjv.smsbot;

public class Applicant {
    public String name;
    public String phone;
    public String gender; // "male" / "female" / "unknown"
    public String jobTitle;
    public String profileUrl;
    public boolean smsSent = false;

    public Applicant(String name, String phone, String gender, String jobTitle, String profileUrl) {
        this.name = name;
        this.phone = phone;
        this.gender = gender;
        this.jobTitle = jobTitle;
        this.profileUrl = profileUrl;
    }

    public String buildSms(String companyPhone) {
        String title = gender.equals("female") ? "خانم " : "آقای ";
        String firstName = name.split(" ")[0];
        return title + firstName + " عزیز،\n" +
            "رزومه شما برای موقعیت «" + jobTitle + "» در شرکت EMT بررسی شد.\n" +
            "خواهشمندیم با شماره " + companyPhone + " تماس بگیرید.\n" +
            "با تشکر - شرکت EMT";
    }
}
