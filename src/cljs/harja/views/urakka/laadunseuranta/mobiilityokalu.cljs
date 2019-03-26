(ns harja.views.urakka.laadunseuranta.mobiilityokalu
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.ikonit :as ikonit]))

(defn mobiilityokalu []
  [:div.mobiilityokalu-container
   [:img.mobiilityokalu {:src "images/mobiilityokalu.jpg"}]
   [:h3 "Esittely"]
   [:p "Tarkastuksien ja havaintojen tekemistä helpottamaan on olemassa erillinen Mobiili laadunseurantatyökalu. Työkalua käytetään kirjaamaan havaintoja, mittauksia ja valokuvia suoraan tien päällä tarkastusajon aikana. Havaintoja voi kirjata joko pikahavaintonapeilla tai omin sanoin lomakkeella. Kun tarkastusajo on valmis, voidaan valita urakka, johon tulokset kirjataan (oletuksena ehdotetaan käyttäjän omaa lähintä urakkaa). Tarkastusten tietoja voi myöhemmin täydentää Harjassa."]
   [:p "Sovellus on tehty ensisijaisesti Android-tableteille, mutta se toimii myös iPadilla. Myös puhelinkäyttö on mahdollista Android-puhelimilla ja iPhonella."]
   [:p [:strong "Laitevaatimukset:"]]
   [:ul
    [:li "Android-tabletti tai iPad"]
    [:li "GPS-yhteys"]
    [:li "Mobiilidatayhteys SIM-kortin avulla mahdollistaa nopeamman GPS–sijainnin löytämisen, internet-yhteyttä tarvitaan tarkastusajon aloittamista ja päättämistä varten)"]
    [:li "Selain: Chrome (53 tai uudempi), Firefox (49 tai uudempi), iPad Safari (9.3 tai uudempi)"]]

   [:p "Kaikkien merkintöjen tallentumiseksi sovellus tulee pitää koko ajan näkyvissä. On myös suositeltavaa asettaa laitteen automaattilukitus pois päältä tai viive mahdollisimman suureksi tarkastusajon ajaksi:"
    [:br] "- iPadissa Asetukset -> Yleiset -> Automaattilukitus -> Pois"
    [:br] "- Androidissa Asetukset -> Näyttö -> Näytön aikakatkaisu -> Pois"]

   [:h3 "Käyttö"]
   [:p "Sisäänkirjautuminen kannattaa tehdä mahdollisuuksien mukaan jo toimistolla ennen ajoa jotta tien päällä ei joudu enää näpyttämään salasanaa."]
   [:p "Ennen kuin aloitat tarkastusajon, odota autossa paikallaan kunnes tarkka GPS-sijainti löytyy ja on nähtävissä kartalta."]
   [:a {:href "https://extranet.vayla.fi/harja/laadunseuranta/"}
    (ikonit/ikoni-ja-teksti (ikonit/livicon-arrow-right)
                            "Siirry Mobiiliin laadunseurantatyökaluun")]
   [:p "Työkalulla kirjatut tarkastukset kirjataan suoraan Harjaan käyttäjän valitsemaan urakkaan."]
   [:a {:href "https://testiextranet.vayla.fi/harja/laadunseuranta/"}
    (ikonit/ikoni-ja-teksti (ikonit/livicon-arrow-right)
                            "Siirry testiympäristöön")]
   [:p "Jos haluat ensin testata työkalua, voit käyttää sitä Harjan testiympäristössä, jossa suoritetut ajot tallentuvat ainoastaan testiympäristöön."]
   [:br]
   [:br]])
