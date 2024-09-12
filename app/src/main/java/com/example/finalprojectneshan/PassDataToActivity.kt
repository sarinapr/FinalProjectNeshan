package com.example.finalprojectneshan

import org.neshan.servicessdk.search.model.Item

interface PassDataToActivity {
    fun passData(item: Item?)
    fun passSecondData(data: Item?)
}
