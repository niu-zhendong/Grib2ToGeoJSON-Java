package org.niuzhendong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.niuzhendong.gribtools.Grib2Process;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static Logger logger = LogManager.getLogger();
    public static void main(String[] args) {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        //Grib2Process.readGrib("C:/Users/niu_z/Documents/git/python/rmf.hgra.2024062422000.grb2");
        //NetCDFProcess netCDFProcess = new NetCDFProcess();
        //netCDFProcess.readGrib("C:/Users/niu_z/Documents/git/python/rmf.hgra.2024062422000.grb2");
        Grib2Process grib2Process = new Grib2Process("C:/Users/niu_z/Documents/git/python/rmf.hgra.2024062422000.grb2");
        grib2Process.convertNetCDFToGeoJSON("C:/Users/niu_z/Documents/git/python/GeoJSON/",true);
        logger.log(Level.INFO,"处理完成！");
    }
}
