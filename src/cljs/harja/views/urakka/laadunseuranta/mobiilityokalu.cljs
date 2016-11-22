(ns harja.views.urakka.laadunseuranta.mobiilityokalu
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.ikonit :as ikonit]))

(defn mobiilityokalu []
  [:div.mobiilityokalu-container
   [:img.mobiilityokalu {:src "images/mobiilityokalu.png"}]
   [:h3 "Esittely"]
   [:p "Tarkastuksien ja havaintojen tekemistä helpottamaan on toteutettu myös erillinen Mobiili laadunseurantatyökalu. Työkalu on tarkoitettu käytettäväksi tien päällä ja se toimii iPadilla ja useimmilla Android-tableteilla. Myös puhelinkäyttö on mahdollista."]
   [:p "Työkalua käytetään kirjaamaan pistemäisiä ja välikohtaisia havaintoja tarkastusajon aikana. Joillekin välikohtaisille havainnoille voi syöttää myös erillisiä mittausarvoja (kuten kitka liukkaalla tiellä). Työkalulla voi kirjata myös yleisiä havaintoja tien päältä. Kun tarkastusajo on valmis, tulokset kirjataan lähimpään urakkaan suoritetun ajon sijainnin perusteella."]
   [:p [:strong "Laitevaatimukset:"]]
   [:ul
    [:li "Android-tabletti tai iPad"]
    [:li "GPS-yhteys"]
    [:li "Internet-yhteys (ainoastaan tarkastusajon aloittamista ja päättämistä varten)"]
    [:li "Selain: Chrome, Androidin ja iPadin oma selain"]]

   [:h3 "Käyttö"]
   [:a {:href "https://extranet.liikennevirasto.fi/harja/laadunseuranta/"}
    (ikonit/ikoni-ja-teksti (ikonit/livicon-arrow-right)
                            "Siirry Mobiiliin laadunseurantatyökaluun")]
   [:p "Työkalulla kirjatut tarkastukset kirjataan suoraan Harjaan lähimpään urakkaan."]
   [:a {:href "https://testiextranet.liikennevirasto.fi/harja/laadunseuranta/"}
    (ikonit/ikoni-ja-teksti (ikonit/livicon-arrow-right)
                            "Siirry testiympäristöön")]
   [:p "Jos haluat vain testata työkalua, voit käyttää sitä Harjan testiympäristössä, jossa suoritetut ajot tallentuvat ainoastaan testiympäristöön."]
   [:br]
   [:br]])