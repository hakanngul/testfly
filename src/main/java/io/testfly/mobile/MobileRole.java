package io.testfly.mobile;

import java.util.List;

/**
 * Element roles for {@code getByRole}. Each role maps to native class names of both platforms, so
 * one query works on Android and iOS. The table is ported from Mobilewright (Apache-2.0); see NOTICE.
 */
public enum MobileRole {
    BUTTON("button", "imagebutton", "floatingactionbutton", "materialbutton", "appcompatbutton"),
    TEXTFIELD("textfield", "securetextfield", "searchfield", "edittext", "appcompatedittext", "textinputedittext", "reactedittext"),
    TEXT("statictext", "textview", "appcompattextview", "materialtextview", "text", "reacttextview"),
    IMAGE("image", "imageview", "appcompatimageview", "shapeableimageview", "reactimageview"),
    SWITCH("switch", "toggle", "togglebutton"),
    CHECKBOX("checkbox"),
    RADIO("radio", "radiobutton", "appcompatradiobutton"),
    SLIDER("slider", "seekbar"),
    PROGRESSBAR("progressindicator", "activityindicator", "progressbar"),
    ALERT("alert", "sheet"),
    COMBOBOX("picker", "pickerwheel", "spinner", "appcompatspinner"),
    LIST("table", "collectionview", "listview", "recyclerview", "scrollview", "reactscrollview"),
    LISTITEM("cell", "linearlayout", "relativelayout", "other"),
    TAB("tab", "tabbar"),
    LINK("link"),
    HEADER("navigationbar", "toolbar", "header");

    private final List<String> nativeTypes;

    MobileRole(String... nativeTypes) {
        this.nativeTypes = List.of(nativeTypes);
    }

    /** Bare, lower-case native class names of this role (e.g. {@code edittext}, {@code textfield}). */
    public List<String> nativeTypes() {
        return nativeTypes;
    }
}
