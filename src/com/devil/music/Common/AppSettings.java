package com.devil.music.Common;

/**
 * Common web application settings.
 *  
 * User: Devil
 * Date: Apr 6, 2017 6:22:38 PM
 */

public class AppSettings {
	
	// Database settings.
	public static final String DATABASE_URL = "localhost";
	public static final int DATABASE_PORT = 27017;
	public static final String DATABASE_NAME = "test_db";
	
	public static final String DATA_PATH = "/home/devil/MSE/data/";
	public static final String INDEX_PATH = "/home/devil/MSE/index.txt";
	public static final String TEST_INDEX_PATH = "F:\\index.txt";
	
	public static final String XIAMI_URL = "http://img.xiami.net/";
	public static final String DEFAULT_IMG_URL = "/MusicServer/img/default.jpg";
	

	
	public static final int SONG_NUM = 12504;
	public static final int CORRELATION_SCORE = 60;
	public static final int LISTENED_SCORE = 20;
	public static final int POPULARITY_SCORE = 20;
	public static final double CORRELATION_THRESHOLD = 1.5;

}
