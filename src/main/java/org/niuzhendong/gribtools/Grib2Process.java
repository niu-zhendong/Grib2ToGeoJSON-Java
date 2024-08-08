package org.niuzhendong.gribtools;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Grib2Process {

    private Logger logger = LogManager.getLogger();
    private NetcdfFile ncfile = null;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private GeometryCollection points = null;
    private Map<String,Object> fileParameters = null;
    private GeoJSONWriter geoJSONWriter = new GeoJSONWriter();
    private GeoJSONReader geoJSONReader = new GeoJSONReader();


    public Grib2Process(String path){
        try {
            ncfile = NetcdfFile.open(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        points = this.calculateCRSParameters("lat","lon");
        fileParameters = this.calculateFileParameters();
        logger.log(Level.INFO,"load success");
    }

    public void convertNetCDFToGeoJSON(String prePath,boolean isGeoOnly){
        if (ncfile == null){
            return ;
        }
        FeatureCollection featureCollection = null;
        List<Variable> variables = ncfile.getVariables();
        for (Variable variable :variables){
            List<Dimension> dimensions = variable.getDimensions();
            DataType dataType = variable.getDataType();
            if (!variable.isCoordinateVariable()){
                switch (variable.getFullName()){
                    case "time1_bounds","reftime" :
                        fileParameters.put(variable.getFullName(),variable.attributes().findAttribute("units").getStringValue());
                        break;
                    case "LatLon_Projection":
                        fileParameters.put("earth_radius",variable.attributes().findAttribute("earth_radius").getNumericValue());
                        break;
                    default:
                        List data = null;
                        try {
                            data = this.convertElements(dataType,variable.read());
                            featureCollection = this.creatfeatureCollection(data,dimensions,isGeoOnly);
                            Boolean flag = false;
                            if (featureCollection.getFeatures().length > 0){
                                flag = this.saveGeoJSONFile(featureCollection,prePath,variable.getFullName()+".geojson");
                            }
                            if (flag){
                                logger.log(Level.INFO,"CoordinateVariable:"+variable.getFullName()+"处理成功！");
                            }else {
                                logger.log(Level.INFO,"CoordinateVariable:"+variable.getFullName()+"处理失败！");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                }
            }
        }
    }

    public FeatureCollection convertNetCDFToGeoJSON(String name){
        if (ncfile == null){
            return null;
        }
        FeatureCollection featureCollection = null;
        Variable variable = this.ncfile.findVariable(name);
        List<Dimension> dimensions = variable.getDimensions();
        DataType dataType = variable.getDataType();
        List data = null;
        try {
            data = this.convertElements(dataType,variable.read());
            featureCollection = this.creatfeatureCollection(data,dimensions,false);
            logger.log(Level.INFO,"CoordinateVariable:"+variable.getFullName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return featureCollection;
    }

    public Feature creatFeature(Geometry geometry,Object data,List<Dimension> dimensions){

        Map<String,Object> props = new HashMap<>();
        props.put("data",data);
        props.put("",this.fileParameters.get(""));
        for (Dimension dimension: dimensions){
            if (dimension.getName().equals("lat")&&dimension.getName().equals("lon")){
                props.put(dimension.getName(),this.fileParameters.get(dimension.getName()));
            }
        }
        return new Feature(geoJSONWriter.write(geometry),props);
    }

    public FeatureCollection creatfeatureCollection(List data, List<Dimension> dimensions,boolean isGeoOnly){
        List<Feature> featureList = new ArrayList<>();
        int num = this.points.getNumGeometries();
        if (data.size()==num){
            for (int i=0; i<num; i++){
                Feature feature = this.creatFeature(this.points.getGeometryN(i),data.get(i),dimensions);
                featureList.add(feature);
            }
        }else{
            if (isGeoOnly){
                logger.log(Level.INFO,"不属于地理信息数据类！");
            }else{
                Feature feature = this.creatFeature(null,data,dimensions);
                featureList.add(feature);
            }
        }
        return new FeatureCollection(featureList.toArray(Feature[]::new));
    }

    public boolean saveGeoJSONFile(FeatureCollection featureCollection,String pefPath,String filename){
        boolean flag = true;
        try {
            File file = new File(pefPath+filename);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            Writer write = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            write.write(featureCollection.toString());
            write.flush();
            write.close();
        } catch (Exception e) {
            flag = false;
            e.printStackTrace();
        }
        return flag;
    }

    public List convertElements(DataType dataType, Array variableArray){
        Long size = variableArray.getSize();
        switch (dataType){
            case DataType.FLOAT:
                float[] floatValue = (float[]) variableArray.copyTo1DJavaArray();
                return this.arrayToFloatList(floatValue);
            case DataType.INT:
                int[] intValue = (int[]) variableArray.copyTo1DJavaArray();
                return Arrays.stream(intValue).boxed().collect(Collectors.toList());
            case DataType.DOUBLE:
                double[] doubleValue = (double[]) variableArray.copyTo1DJavaArray();
                return Arrays.stream(doubleValue).boxed().collect(Collectors.toList());
            default:
                logger.log(Level.INFO,"default process");
                return null;
        }
    }

    private List<Float> arrayToFloatList(float[] array) {
        List<Float> floatList = new ArrayList<>();
        for (float value : array) {
            floatList.add(value);
        }
        return floatList;
    }

    public GeometryCollection calculateCRSParameters(String name_of_lat,String name_of_lon){
        Variable latVariable = this.ncfile.findVariable(name_of_lat);
        Variable lonVariable = this.ncfile.findVariable(name_of_lon);
        float[] latArray = new float[0];
        float[] lonArray = new float[0];
        try {
            if (latVariable != null && lonVariable != null) {
                latArray = (float[]) latVariable.read().copyTo1DJavaArray();
                lonArray = (float[]) lonVariable.read().copyTo1DJavaArray();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<Geometry> croods = new ArrayList<>();
        for(int i = 0; i<latArray.length; i++){
            for (int j = 0; j<lonArray.length; j++){
                croods.add(this.convertCroodsToPoint(lonArray[j],latArray[i]));
            }
        }
        return geometryFactory.createGeometryCollection(croods.toArray(new Geometry[0]));
    }

    public Map<String,Object> calculateFileParameters(){
        List<Variable> variables = ncfile.getVariables();
        Map<String,Object> fileParameters = new HashMap<String,Object>();
        for (Variable variable :variables){
            List<Dimension> dimensions = variable.getDimensions();
            DataType dataType = variable.getDataType();
            if (variable.isCoordinateVariable()){
                logger.log(Level.INFO,"calculateFileParameters:"+variable.getFullName());
                try {
                    switch (variable.getFullName()){
                        case "time","time1","time1_bounds","reftime" :
                            fileParameters.put(variable.getFullName(),variable.attributes().findAttribute("units").getStringValue());
                            break;
                        case "lon","lat" :
                            break;
                        case "LatLon_Projection":
                            fileParameters.put("earth_radius",variable.attributes().findAttribute("earth_radius"));
                            break;
                        default:
                            List data = this.convertElements(dataType,variable.read());
                            if (data.size()==1){
                                fileParameters.put(variable.getFullName(),data.getFirst());
                            }else{
                                fileParameters.put(variable.getFullName(),data);
                            }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return fileParameters;
    }

    public Geometry convertCroodsToPoint(double lan,double lon){
        Coordinate coordinate = new Coordinate(lan,lon);
        return geometryFactory.createPoint(coordinate);
    }
}
