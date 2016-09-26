package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import model.Restaurant;
import yelp.YelpAPI;

public class MySQLDBConnection implements DBConnection {

	private Connection conn = null;
	private static final int MAX_RECOMMENDED_RESTAURANTS = 10;

	public MySQLDBConnection() {
		this(DBUtil.URL);
	}

	public MySQLDBConnection(String url) {
		try {
			// Forcing the class representing the MySQL driver to load and
			// initialize.
			// The newInstance() call is a work around for some broken Java
			// implementations
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) { /* ignored */
			}
		}
	}

	@Override
	public void setVisitedRestaurants(String userId, List<String> businessIds) {
		// TODO save into history table for each userId businessId pair
		try {
			String sql = "INSERT INTO history (user_id, business_id) values (?,?)";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			for (String businessId : businessIds) {
				statement.setString(2,  businessId);
				statement.executeUpdate();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void unsetVisitedRestaurants(String userId, List<String> businessIds) {
		// TODO Auto-generated method stub
		String query = "DELETE FROM history WHERE user_id = ? and business_id = ?";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			for (String businessId : businessIds) {
				statement.setString(1,  userId);
				statement.setString(2, businessId);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Set<String> getVisitedRestaurants(String userId) {
		Set<String> visitedRestaurants = new HashSet<String>();
		try {
			String sql = "SELECT business_id from history WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String visitedRestaurant = rs.getString("business_id");
				visitedRestaurants.add(visitedRestaurant);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return visitedRestaurants;
	}
	
	@Override
	public List<String> getVisitedRestaurantsByVisitedTime(String userId) {
		List<String> visitedRestaurants = new ArrayList<String>();
		try {
			String sql = "SELECT business_id from history WHERE user_id = ? ORDER BY last_visited_time DESC";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String visitedRestaurant = rs.getString("business_id");
				visitedRestaurants.add(visitedRestaurant);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return visitedRestaurants;
	}

	@Override
	public JSONObject getRestaurantsById(String businessId, boolean isVisited) {
		// TODO Auto-generated method stub
		try {
			String sql = "SELECT * from restaurants where business_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, businessId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				Restaurant restaurant = new Restaurant(
						rs.getString("business_id"), rs.getString("name"),
						rs.getString("categories"), rs.getString("city"),
						rs.getString("state"), rs.getFloat("stars"),
						rs.getString("full_address"), rs.getFloat("latitude"),
						rs.getFloat("longitude"), rs.getString("image_url"),
						rs.getString("url"));
				JSONObject obj = restaurant.toJSONObject();
				obj.put("is_visited", isVisited);
				return obj;
			}
		} catch (Exception e) { /* report an error */
			System.out.println(e.getMessage());
		}
		return null;
	}

	@Override
	public JSONArray recommendRestaurants(String userId) {
		// TODO Auto-generated method stub
		try {
			if (conn == null) {
				return null;
			}

			Set<String> visitedRestaurants = getVisitedRestaurants(userId);
			Map<String, Integer> allCategories = new HashMap<String, Integer>();// why hashSet?
			for (String restaurant : visitedRestaurants) {
				for (String category: getCategories(restaurant)) {
					Integer count = allCategories.get(category);
					if (count == null) {
						allCategories.put(category, 1);
					} else {
						allCategories.put(category,  count + 1);
					}
				}
			}
			Set<String> allRestaurants = new HashSet<>();
			List<String> rs = new ArrayList<>();
			List<Map.Entry<String, Integer>> entry = new LinkedList<>(allCategories.entrySet());
			Collections.sort(entry, 
					new Comparator<Map.Entry<String, Integer>>() {
						@Override
						public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
							return e2.getValue().compareTo(e1.getValue());
						}
					});
			for (Map.Entry<String, Integer> e : entry) {
				String category = e.getKey();
				for (String businessId : getBusinessId(category)) {
					if (allRestaurants.add(businessId)) {
						rs.add(businessId);
					}
				}
			}
			List<JSONObject> diff = new ArrayList<>();
			int count = 0;
			for (String businessId : rs) {
				// Perform filtering
				if (!visitedRestaurants.contains(businessId)) {
					diff.add(getRestaurantsById(businessId, false));
					count++;
					if (count >= MAX_RECOMMENDED_RESTAURANTS) {
						break;
					}
				}
			}
			return new JSONArray(diff);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}
	
	@Override
	public JSONArray recommendRestaurantsByLocation(String userId, double lat, double lon) {
		// TODO Auto-generated method stub
		try {
			if (conn == null) {
				return null;
			}
			JSONArray array = searchRestaurants(userId, lat, lon);
			Set<String> nearbyRestaurants = new HashSet<>();
			for (int i = 0; i < array.length(); i++) {
				JSONObject obj = array.getJSONObject(i);
				String restaurant = obj.getString("business_id");
				nearbyRestaurants.add(restaurant);
			}
			Set<String> visitedRestaurants = getVisitedRestaurants(userId);
			Map<String, Integer> allCategories = new HashMap<String, Integer>();// why hashSet?
			for (String restaurant : visitedRestaurants) {
				for (String category: getCategories(restaurant)) {
					Integer count = allCategories.get(category);
					if (count == null) {
						allCategories.put(category, 1);
					} else {
						allCategories.put(category,  count + 1);
					}
				}
			}
			Set<String> allRestaurants = new HashSet<>();
			List<String> rs = new ArrayList<>();
			List<Map.Entry<String, Integer>> entry = new LinkedList<>(allCategories.entrySet());
			Collections.sort(entry, 
					new Comparator<Map.Entry<String, Integer>>() {
						@Override
						public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
							return e2.getValue().compareTo(e1.getValue());
						}
					});
			for (Map.Entry<String, Integer> e : entry) {
				String category = e.getKey();
				for (String businessId : getBusinessId(category)) {
					if (allRestaurants.add(businessId) && nearbyRestaurants.contains(businessId)) {
						rs.add(businessId);
					}
				}
			}
			List<JSONObject> diff = new ArrayList<>();
			int count = 0;
			for (String businessId : rs) {
				// Perform filtering
				if (!visitedRestaurants.contains(businessId)) {
					diff.add(getRestaurantsById(businessId, false));
					count++;
					if (count >= MAX_RECOMMENDED_RESTAURANTS) {
						break;
					}
				}
			}
			return new JSONArray(diff);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	@Override
	public Set<String> getCategories(String businessId) {
		// TODO Auto-generated method stub
		Set<String> set = new HashSet<>();
		try {
			String sql = "SELECT categories from restaurants WHERE business_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, businessId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				String[] categories = rs.getString("categories").split(",");
				for (String category : categories) {
					set.add(category.trim());
				}
			}
			
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println(e.getMessage());
		}
		return set;
	}

	@Override
	public Set<String> getBusinessId(String category) {
		// TODO Auto-generated method stub
		Set<String> set = new HashSet<>();
		try {
			String sql = "SELECT business_id from restaurants WHERE categories LIKE ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, "%" + category + "%");
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String businessId = rs.getString("business_id");
				set.add(businessId);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return set;
	}

	@Override
	public JSONArray searchRestaurants(String userId, double lat, double lon) {
		try {
			YelpAPI api = new YelpAPI();
			JSONObject response = new JSONObject(
					api.searchForBusinessesByLocation(lat, lon));
			JSONArray array = (JSONArray) response.get("businesses");

			Map<Double, List<JSONObject>> map = new HashMap<Double, List<JSONObject>>();
			Set<String> visited = getVisitedRestaurants(userId);

			for (int i = 0; i < array.length(); i++) {
				JSONObject inputObject = array.getJSONObject(i);
				Restaurant restaurant = new Restaurant(inputObject);
				String businessId = restaurant.getBusinessId();
				String name = restaurant.getName();
				String categories = restaurant.getCategories();
				String city = restaurant.getCity();
				String state = restaurant.getState();
				String fullAddress = restaurant.getFullAddress();
				double stars = restaurant.getStars();
				double latitude = restaurant.getLatitude();
				double longitude = restaurant.getLongitude();
				String imageUrl = restaurant.getImageUrl();
				String url = restaurant.getUrl();
				
				
				JSONObject outputObject = restaurant.toJSONObject();
				if (visited.contains(businessId)) {
					outputObject.put("is_visited", true);
				} else {
					outputObject.put("is_visited", false);
				}
				
				
				String sql = "INSERT IGNORE INTO restaurants VALUES (?,?,?,?,?,?,?,?,?,?,?)";
				PreparedStatement statement = conn.prepareStatement(sql);
				statement.setString(1, businessId);
				statement.setString(2, name);
				statement.setString(3, categories);
				statement.setString(4, city);
				statement.setString(5, state);
				statement.setDouble(6, stars);
				statement.setString(7, fullAddress);
				statement.setDouble(8, latitude);
				statement.setDouble(9, longitude);
				statement.setString(10, imageUrl);
				statement.setString(11, url);
				statement.execute();
				List<JSONObject> list = map.get(stars);
				if (list == null) {
					list = new ArrayList<JSONObject>();
					map.put(stars, list);
				}
				list.add(outputObject);
			}
			List<JSONObject> rs = new ArrayList<JSONObject>();
			Object[] keySet = map.keySet().toArray();
			Arrays.sort(keySet, 
					new Comparator<Object>() {
						@Override
						public int compare(Object o1, Object o2) {
							Double d1 = (Double) o1;
							Double d2 = (Double) o2;
							return d2.compareTo(d1);
						}
					});
			for (Object key : keySet) {
				for (JSONObject obj : map.get((Double) key)) {
					rs.add(obj);
				}
			}

			return new JSONArray(rs);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	
	@Override
	public Boolean verifyLogin(String userId, String password) {
		try {
			if (conn == null) {
				return false;
			}

			String sql = "SELECT user_id from users WHERE user_id = ? and password = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			statement.setString(2, password);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				return true;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	@Override
	public String getFirstLastName(String userId) {
		String name = "";
		try {
			if (conn != null) {
				String sql = "SELECT first_name, last_name from users WHERE user_id = ?";
				PreparedStatement statement = conn.prepareStatement(sql);
				statement.setString(1, userId);
				ResultSet rs = statement.executeQuery();
				if (rs.next()) {
					name += rs.getString("first_name") + " "
							+ rs.getString("last_name");
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return name;
	}

}
