(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.palautteet
  (:require [reagent.core :as r]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

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

(defn- varmista-kayttajalta [komponentti]
  (r/with-let [suoritettu (r/atom 0)]
    [kehys/kortti komponentti
     [:p "Toiminto ajetaan vain, jos käyttäjä hyväksyy dialogin. Peruuttaminen ei suorita toimintoa."]
     [napit/nappi "Avaa varmistus"
      #(varmista/varmista-kayttajalta
         {:otsikko "Vahvista toiminto"
          :sisalto [:p "Haluatko varmasti jatkaa?"]
          :hyvaksy "Jatka"
          :toiminto-fn (fn [] (swap! suoritettu inc))})
      {:data-cy (kehys/ankkuri komponentti "-avaa")
       :luokka "nappi-toissijainen"}]
     [:p {:data-cy (kehys/ankkuri komponentti "-tila")}
      (str "Suoritettu: " @suoritettu)]]))

(register! :ajax-loader {:esikatselu ajax-loader})
(register! :vihje {:esikatselu vihje})
(register! :info-laatikko {:esikatselu info-laatikko})
(register! :varmista-kayttajalta {:esikatselu varmista-kayttajalta})
