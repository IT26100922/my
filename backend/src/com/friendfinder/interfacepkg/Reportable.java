package com.friendfinder.interfacepkg;

import java.sql.SQLException;
import java.util.Map;

public interface Reportable {
    Map<String,Object> generateReport() throws SQLException;
}
