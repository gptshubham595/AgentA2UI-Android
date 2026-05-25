package com.shubham.agentui

internal val ReceivedFlightsJson = """
{
  "flights": [
    {
      "id": "aa-1140",
      "airline": "American Airlines",
      "airlineLogo": "https://www.google.com/s2/favicons?domain=aa.com&sz=128",
      "flightNumber": "AA 1140",
      "origin": "SFO",
      "destination": "JFK",
      "date": "Tue, Mar 18",
      "departureTime": "12:20",
      "arrivalTime": "20:55",
      "duration": "5h 35m",
      "status": "On Time",
      "price": "${'$'}298"
    },
    {
      "id": "b6-1218",
      "airline": "JetBlue Airways",
      "airlineLogo": "https://www.google.com/s2/favicons?domain=jetblue.com&sz=128",
      "flightNumber": "B6 1218",
      "origin": "SFO",
      "destination": "JFK",
      "date": "Tue, Mar 18",
      "departureTime": "14:05",
      "arrivalTime": "22:30",
      "duration": "5h 25m",
      "status": "Boarding",
      "price": "${'$'}264"
    },
    {
      "id": "as-32",
      "airline": "Alaska Airlines",
      "airlineLogo": "https://www.google.com/s2/favicons?domain=alaskaair.com&sz=128",
      "flightNumber": "AS 32",
      "origin": "SFO",
      "destination": "JFK",
      "date": "Tue, Mar 18",
      "departureTime": "21:30",
      "arrivalTime": "05:50",
      "duration": "5h 20m",
      "status": "On Time",
      "price": "${'$'}276"
    }
  ]
}
""".trimIndent()
