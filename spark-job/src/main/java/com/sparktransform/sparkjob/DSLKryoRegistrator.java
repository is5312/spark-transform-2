package com.sparktransform.sparkjob;

import com.esotericsoftware.kryo.Kryo;
import com.sparktransform.dsl.DSLExecutor;
import org.apache.spark.serializer.KryoRegistrator;

/**
 * Custom Kryo registrator for DSL transformation classes
 * This helps Kryo serialize our custom classes properly and avoid SerializedLambda issues
 */
public class DSLKryoRegistrator implements KryoRegistrator {
    
    @Override
    public void registerClasses(Kryo kryo) {
        // Register our custom DSL classes
        kryo.register(CSVTransformJob.SimpleDSLMapFunction.class);
        kryo.register(DSLExecutor.class);
        
        // Register common Java classes that might be used
        kryo.register(java.util.HashMap.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(java.util.LinkedHashMap.class);
        kryo.register(java.lang.String.class);
        kryo.register(java.lang.Integer.class);
        kryo.register(java.lang.Double.class);
        kryo.register(java.lang.Long.class);
        kryo.register(java.lang.Boolean.class);
        
        // Register Jackson classes used by DSL
        kryo.register(com.fasterxml.jackson.databind.JsonNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.ObjectNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.ArrayNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.TextNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.IntNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.DoubleNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.BooleanNode.class);
        kryo.register(com.fasterxml.jackson.databind.node.NullNode.class);
        
        // Register ObjectMapper
        kryo.register(com.fasterxml.jackson.databind.ObjectMapper.class);
        
        // Register String arrays
        kryo.register(String[].class);
        kryo.register(Object[].class);
        
        // Register Row and related classes
        kryo.register(org.apache.spark.sql.Row.class);
        kryo.register(org.apache.spark.sql.RowFactory.class);
        kryo.register(org.apache.spark.sql.types.StructType.class);
        kryo.register(org.apache.spark.sql.types.StructField.class);
        kryo.register(org.apache.spark.sql.types.DataType.class);
        kryo.register(org.apache.spark.sql.types.DataTypes.class);
        
        // Register common data types
        kryo.register(org.apache.spark.sql.types.StringType.class);
        kryo.register(org.apache.spark.sql.types.IntegerType.class);
        kryo.register(org.apache.spark.sql.types.LongType.class);
        kryo.register(org.apache.spark.sql.types.DoubleType.class);
        kryo.register(org.apache.spark.sql.types.BooleanType.class);
        kryo.register(org.apache.spark.sql.types.TimestampType.class);
        kryo.register(org.apache.spark.sql.types.DateType.class);
    }
}
