(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.palautteet
  (:require [reagent.core :as r]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.napit :as napit]
            [harja.ui.sivupalkki :as sivupalkki]
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

(defn- leijuke [komponentti]
  (r/with-let [auki? (r/atom false)]
    [kehys/kortti komponentti
     [:p "Sisällön päälle avautuva paneeli. Sulkeutuu ruksilla tai ESC-näppäimellä."]
     [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
      [napit/nappi "Avaa leijuke" #(swap! auki? not)
       {:data-cy (kehys/ankkuri komponentti "-avaa")
        :luokka "nappi-toissijainen"}]
      (when @auki?
        [leijuke/leijuke {:otsikko "Leijukkeen otsikko"
                          :sulje! #(reset! auki? false)
                          :tallenna-fn #(reset! auki? false)
                          :peruuta-fn #(reset! auki? false)}
         [:p "Leijukkeen sisältö."]])]]))

(register! :ajax-loader {:esikatselu ajax-loader})
(register! :vihje {:esikatselu vihje})
(register! :info-laatikko {:esikatselu info-laatikko})
(register! :varmista-kayttajalta {:esikatselu varmista-kayttajalta})
(defn- tooltip [komponentti]
  [kehys/kortti komponentti
   [:p "Sisältö näkyy kohteen päällä hiiren viennillä tai fokuksella."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/tooltip {:suunta :oikea}
     [:span {:data-cy (kehys/ankkuri komponentti "-kohde")} "Vie hiiri tähän"]
     "Tooltipin sisältö"]]])

(defn- tila-indikaattori [komponentti]
  [kehys/kortti komponentti
   [:p "Pallo ja teksti tilan näyttämiseen. Tekstin muotoilun antaa käyttöpaikka."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}]
    [yleiset/tila-indikaattori "kesken" {:fmt-fn (constantly "Kesken")}]
    [yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Hylätty")}]]])

(defn- toast-viesti [komponentti]
  [kehys/kortti komponentti
   [:p "Pieni inline-ilmoitus ikonilla. Luokka \"varoitus\" vaihtaa tyylin."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [yleiset/toast-viesti "Tallennus onnistui"]
    [:div {:data-cy (kehys/ankkuri komponentti "-varoitus")}
     [yleiset/toast-viesti "Tarkista tiedot" "varoitus"]]]])

(defn- sivupalkki-esikatselu [komponentti]
  (r/with-let [auki? (r/atom false)]
    [kehys/kortti komponentti
     [:p "Oikealle avautuva sivupalkki. Näkymä päättää avaamisen ja sulkemisen. Taustan klikkaus sulkee sen."]
     [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
      [napit/nappi "Avaa sivupalkki" #(reset! auki? true)
       {:data-cy (kehys/ankkuri komponentti "-avaa")
        :luokka "nappi-toissijainen"}]]
     (when @auki?
       [sivupalkki/oikea {:leveys "600px" :jarjestys 1 :sulku-fn #(reset! auki? false)}
        [:div {:data-cy (kehys/ankkuri komponentti "-sisalto")}
         [:h3 "Sivupalkin sisältö"]
         [napit/nappi "Sulje" #(reset! auki? false)
          {:data-cy (kehys/ankkuri komponentti "-sulje")
           :luokka "nappi-toissijainen"}]]])]))

(register! :leijuke {:esikatselu leijuke})
(register! :sivupalkki {:esikatselu sivupalkki-esikatselu})
(register! :toast-viesti {:esikatselu toast-viesti})
(register! :tila-indikaattori {:esikatselu tila-indikaattori})
(register! :tooltip {:esikatselu tooltip})
