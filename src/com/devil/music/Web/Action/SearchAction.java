package com.devil.music.Web.Action;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.*;
import static com.mongodb.client.model.Updates.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.devil.music.Common.AppSettings;
import com.devil.music.Common.DateUtility;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.mongodb.client.MongoCursor;

/**
 * Search songs, singers, lyrics, user listen history.
 * 
 * User: Devil Date: Apr 19, 2017 1:33:26 PM
 */

public class SearchAction extends BasicAction {
	private String searchContent;
	private int searchType;
	private int songId;
	private int singerId;

	public String search() {
		JSONArray list = new JSONArray();
		String result = "";
		MongoCursor<Document> iterator = null;
		switch (searchType) {
		case TYPE_SONG: {
			iterator = getCollection("song").find(regex("title", searchContent, "i")).sort(descending("collect_count"))
					.iterator();
			songFilter(iterator, list);
			break;
		}
		case TYPE_SINGER: {
			iterator = getCollection("song").find(regex("singer_info.artistName", searchContent, "i"))
					.sort(descending("collect_count")).iterator();
			songFilter(iterator, list);
			break;
		}
		case TYPE_LYRIC: {
			HanLP.Config.Normalization = true;
			List<Term> termList = HanLP.segment(searchContent);
			Map<Integer, Double> map = new HashMap<Integer, Double>();
			double MAX_tf_idf = 0;
			double MAX_collect_num = 0;
			int flag = 0;
			for(Term t:termList){
				if(!t.nature.startsWith('w')){
					String word = t.word;
					//System.out.println("===="+word);
					Document doc = getCollection("posting").find(eq("name",word)).first();
					if(doc == null){
						System.out.println("Info: can't find word:"+word);
						continue;
					}
					double df = doc.getInteger("nums");
					double idf = Math.log10(AppSettings.SONG_NUM/df);
					
					if(df<1000){
						flag = 1;
					}
					
					JSONArray array = new JSONObject(doc.toJson()).getJSONArray("list");
					for(int i=0;i<array.length();i++){
						JSONObject obj = array.getJSONObject(i);
						int id = obj.getInt("id");
						int tf = obj.getInt("nums");
						double itf = 1 + Math.log10(tf);
						double tf_idf = itf*idf;
						if(map.containsKey(id)){
							tf_idf += map.get(id);
						}
						if(tf_idf>MAX_tf_idf){
							MAX_tf_idf = tf_idf;
						}
						map.put(id, tf_idf);
						//System.out.println("id="+ id+",tf_idf="+tf_idf);
					}
				}
			}
			if(flag == 0){
				iterator = getCollection("song").find(regex("lyrics", searchContent, "i")).sort(descending("collect_count")).limit(100).iterator();
				songFilter(iterator, list);
				break;
			}
			if(getSession().containsKey("username")){
				String username = (String) getSession().get("username");
				MongoCursor<Document> hiterator = getCollection("history").find(eq("username",username)).iterator();
				while(hiterator.hasNext()){
					Document history = hiterator.next();
					int song_id = history.getInteger("song_id");
					if(map.containsKey(song_id)&&map.get(song_id)>=AppSettings.CORRELATION_THRESHOLD){
						double value = 100 + map.get(song_id);
						map.put(song_id, value);
					}
				}
			}
			List<JSONObject> dlist = new ArrayList<JSONObject>();
			for(Map.Entry<Integer, Double> entry:map.entrySet()){
				if(entry.getValue()<AppSettings.CORRELATION_THRESHOLD){
					continue;
				}
				Document doc = getCollection("song").find(eq("song_id", entry.getKey())).first();
				JSONObject obj = new JSONObject(doc.toJson());
				double tf_idf = entry.getValue()>=100?entry.getValue()-100:entry.getValue();
				tf_idf = tf_idf*AppSettings.CORRELATION_SCORE/MAX_tf_idf;
				obj.put("tf-idf", tf_idf);
				obj.put("listened", entry.getValue()>=100?true:false);
				if(obj.getInt("collect_count") > MAX_collect_num){
					MAX_collect_num = obj.getInt("collect_count");
				}
				dlist.add(obj);
			}
			for(JSONObject obj:dlist){
				int tf_idf = (int)obj.getDouble("tf-idf");
				int listened = (int)(obj.getBoolean("listened")?AppSettings.LISTENED_SCORE:0);
				int pop = (int)(obj.getInt("collect_count")*AppSettings.POPULARITY_SCORE/MAX_collect_num);
				int score = tf_idf+listened+pop;
				obj.put("collect_count", tf_idf+"+"+listened+"+"+pop+"="+score);
				obj.put("score", score);
			}
			Collections.sort(dlist, new Comparator<JSONObject>(){
				@Override
				public int compare(JSONObject arg0, JSONObject arg1) {
					return arg1.getInt("score")-arg0.getInt("score");
				}
			});
			
			int num = 0;
			for(JSONObject obj:dlist){
				if(num<100){
					//System.out.println(obj.getInt("score"));
					list.put(obj);
					num++;
				}
				else{
					break;
				}
			}
			
			//iterator = getCollection("song").find(regex("lyrics", searchContent, "i")).sort(descending("collect_count")).iterator();

			break;
		}
		default: {
			System.out.println("Error: unknown search type.");
			break;
		}
		}
		/*
		while (iterator.hasNext()) {
			Document doc = iterator.next();
			JSONObject object = new JSONObject(doc.toJson());
			list.put(object);
		}
		*/
		
		result = list.toString();
		//System.out.println(result);
		sendResponse(result);
		return null;
	}
	
	
	
	public void songFilter(MongoCursor<Document> iterator, JSONArray list){
		Set<Integer> set= new HashSet<Integer>();
		if(getSession().containsKey("username")){
			String username = (String) getSession().get("username");
			MongoCursor<Document> hiterator = getCollection("history").find(eq("username",username)).sort(descending("listenNum")).iterator();
			while(hiterator.hasNext()){
				Document history = hiterator.next();
				int song_id = history.getInteger("song_id");
				set.add(song_id);
			}
			ArrayList<JSONObject> array = new ArrayList<JSONObject>();
			int num = 0;
			while (iterator.hasNext()) {
				Document doc = iterator.next();
				JSONObject object = new JSONObject(doc.toJson());
				if(set.contains(doc.getInteger("song_id"))){
					object.put("collect_count", "* "+object.getInt("collect_count"));
					array.add(num, object);
					num++;
				}
				else{
					array.add(object);
				}
			}
			for(JSONObject obj:array){
				list.put(obj);
			}
		}	
		else{
			while (iterator.hasNext()) {
				Document doc = iterator.next();
				JSONObject object = new JSONObject(doc.toJson());
				list.put(object);
			}
		}
	}

	public String getUserInfo() {
		if(getSession().containsKey("username")){
			String username = (String) getSession().get("username");
			MongoCursor<Document> iterator = getCollection("history").find(eq("username",username)).sort(descending("listenNum")).iterator();
			JSONArray list = new JSONArray();
			while(iterator.hasNext()){
				Document history = iterator.next();
				int song_id = history.getInteger("song_id");
				Document song = getCollection("song").find(eq("song_id",song_id)).first();
				JSONObject song_obj = new JSONObject(song.toJson());
				song_obj.put("lastModified", DateUtility.getTextFromTime(history.getDate("lastModified")));
				song_obj.put("clickNum", history.getInteger("clickNum"));
				song_obj.put("listenNum", history.getInteger("listenNum"));
				list.put(song_obj);
			}
			String result = list.toString();
			//System.out.println(result);
			sendResponse(result);
		}
		return null;
	}
	
	// listen once, all of clickNum,listenNum,collect_count +=1;
	public String saveListenHistory(){
		if(getSession().containsKey("username")){	
			String username = (String) getSession().get("username");
			getCollection("song").findOneAndUpdate(eq("song_id",songId), inc("collect_count",1));
			Document doc = getCollection("history").findOneAndUpdate(and(eq("username",username),eq("song_id",songId)), combine(currentDate("lastModified"),inc("listenNum",1),inc("clickNum",1)));	
			// first listen this song.
			if(doc == null){
				Document history = new Document("username",username).append("song_id", songId);
				history.append("lastModified", new Date());
				history.append("clickNum", 1);
				history.append("listenNum", 1);
				getCollection("history").insertOne(history);
			}
		}
		return null;
	}
	
	// click once, only clickNum +=1;
	public String saveClickHistory(){
		if(getSession().containsKey("username")){
			String username = (String) getSession().get("username");
			Document doc = getCollection("history").findOneAndUpdate(and(eq("username",username),eq("song_id",songId)), inc("clickNum",1));
			// first click this song.
			if(doc == null){
				Document history = new Document("username",username).append("song_id", songId);
				history.append("lastModified", new Date());
				history.append("clickNum", 1);
				history.append("listenNum", 0);
				getCollection("history").insertOne(history);
			}
		}
		return null;
	}
	
	public String showSongDetail(){
		saveClickHistory();
		Document song = getCollection("song").find(eq("song_id",songId)).first();
		JSONObject song_obj = new JSONObject(song.toJson());
		sendResponse(song_obj.toString());
		return null;
	}
	
	public String showSingerDetail(){
		Document singer = getCollection("singer").find(eq("artistId",singerId)).first();
		JSONObject singer_obj = new JSONObject(singer.toJson());
		
		// cut and regenerate singer image url.
		// xiami image file has five types: 
		// no image; end with .xxx; end with _1.xxx; end with _1_1.xxx; end with _2_1.xxx;
		// xxx is jpg/png/ or other types.
		// _1/_1_1/_2_1.xxx are all small images, so change all of them to .xxx;
		String artistLogo = singer_obj.getString("artistLogo");
		String img_url = "";
		if (artistLogo.equals("")){
			img_url = AppSettings.DEFAULT_IMG_URL;
		}
		else{
			img_url = artistLogo.replace("_1.", ".");
			img_url = img_url.replace("_1.", ".");
			img_url = img_url.replace("_2.", ".");
			img_url = AppSettings.XIAMI_URL + img_url;
		}
		singer_obj.put("img_url", img_url);
		System.out.println(artistLogo);
		System.out.println(img_url);
		
		// join song list of the given singer.
		JSONArray list = new JSONArray();
		MongoCursor<Document> iterator = getCollection("song").find(eq("singer_ids", singerId))
				.sort(descending("collect_count")).iterator();
		/*
		while (iterator.hasNext()) {
			Document doc = iterator.next();
			JSONObject object = new JSONObject(doc.toJson());
			list.put(object);
		}
		*/
		songFilter(iterator, list);
		singer_obj.put("song_list", list);
		
		sendResponse(singer_obj.toString());
		return null;
	}

	public String getSearchContent() {
		return searchContent;
	}

	public void setSearchContent(String searchContent) {
		this.searchContent = searchContent;
	}

	public int getSearchType() {
		return searchType;
	}

	public void setSearchType(int searchType) {
		this.searchType = searchType;
	}

	public int getSongId() {
		return songId;
	}

	public void setSongId(int songId) {
		this.songId = songId;
	}

	public int getSingerId() {
		return singerId;
	}

	public void setSingerId(int singerId) {
		this.singerId = singerId;
	}

}
