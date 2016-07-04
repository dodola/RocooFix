/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.lody.legend.dalvik;

import com.lody.legend.utility.LegendNative;
import com.lody.legend.utility.Struct;
import com.lody.legend.utility.StructMapping;
import com.lody.legend.utility.StructMember;

/**
 * Created by sunpengfei on 16/7/3.
 */

public class ObjectStruct extends Struct {
    @StructMapping(offset = 0)
    public StructMember clazz;

    @StructMapping(offset = 8, length = 16)
    public StructMember instanceData;


    @StructMapping(offset = 24)
    public StructMember descriptor;

    @StructMapping(offset = 28)
    public StructMember descriptorAlloc;

    @StructMapping(offset = 32, length = 4)
    public StructMember accessFlags;



    //    struct ClassObject {
//        struct Object o; // emulate C++ inheritance, Collin
//    /* leave space for instance data; we could access fields directly if we
//       freeze the definition of java/lang/Class */
//        u4 instanceData[CLASS_FIELD_SLOTS];
//
//    /* UTF-8 descriptor for the class; from constant pool, or on heap
//       if generated ("[C") */
//        const char *descriptor;
//        char *descriptorAlloc;
//
//    /* access flags; low 16 bits are defined by VM spec */
//        u4 accessFlags;
//    private ObjectStruct(Object myobjects) {
//        super(LegendNative.getObjectAddress(myobjects));
//        this.objectStruct = myobjects;
//    }
    private ObjectStruct(long address){
        super(address);
    }

    public static ObjectStruct of(long objectStruct) {
        return new ObjectStruct(objectStruct);
    }
}
