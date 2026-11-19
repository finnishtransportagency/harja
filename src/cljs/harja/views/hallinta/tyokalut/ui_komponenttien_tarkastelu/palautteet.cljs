(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.palautteet
  (:require [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

(defn- ajax-loader [komponentti]
  [kehys/kortti komponentti
   [:p "Latauskuva viestillä tai ilman. Sama-rivi? sijoittaa viestin kuvan viereen."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/ajax-loader "Haetaan tietoja"]
    [:div {:data-cy (kehys/ankkuri komponentti "-sama-rivi")}
     [yleiset/ajax-loader "Haetaan tietoja" {:sama-rivi? true}]]]])

(defn- vihje [komponentti]
  [kehys/kortti komponentti
   [:p "Kevyt ohjeteksti kuvakkeella."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/vihje "Kentän arvo tallentuu automaattisesti."]]])

(defn- info-laatikko [komponentti]
  [kehys/kortti komponentti
   [:p "Tyypitelty ilmoitus. Tyyppi määrää ikonin ja värin."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    (for [tyyppi [:varoitus :onnistunut :neutraali :huolto]]
      [:div {:key (name tyyppi)
             :data-cy (kehys/ankkuri komponentti "-" (name tyyppi))}
       [yleiset/info-laatikko tyyppi (str "Esimerkkiviesti: " (name tyyppi))]])]])

(def esikatselut
  {:ajax-loader ajax-loader
   :vihje vihje
   :info-laatikko info-laatikko})
