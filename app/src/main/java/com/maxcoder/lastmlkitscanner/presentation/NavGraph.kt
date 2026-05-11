package com.maxcoder.lastmlkitscanner.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
//import com.maxcoder.lastmlkitscanner.NavGraphs
import com.ramcosta.composedestinations.DestinationsNavHost

@Composable
fun NavGraph() {
    DestinationsNavHost(
        navGraph = NavGraphs.root,
        navController = rememberNavController()
    )
}