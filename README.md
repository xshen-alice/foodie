#Foodie: a restaurant recommendation web app
Foodie aims to use personalization to improve resturant search and recommendation based on search history and favorite record.
#API Reference:
All APIs are in the src/api directory. 
- SearchRestaurants: to search nearby restaurants
- VisitHistory: to get visited restaurants for specific user
- recommendRestaurants: to get recommend restaurants for sepecific user
- LoginServlet: to login
- LogoutServlet: to logout
- RpcParser: parser of HTTP request and response

#Hisotry: last updated 26 Sep 2016
- Sort nearby restaurants by number of stars
- Sort favourite restaurants by time added
- Count the frequency of categories in favourite restaurants
- Sort recommend restaurnats by the freqency of categories
- Filter the recommend restaurants so that only the nearby restaurants will be recommended.
