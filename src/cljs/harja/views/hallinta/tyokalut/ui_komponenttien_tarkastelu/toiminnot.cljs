(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.toiminnot
  (:require [reagent.core :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(defn- nappi [komponentti]
  (r/with-let [suoritettu? (r/atom false)]
    [kehys/kortti komponentti
     [:p "Teksti, callback ja käyttöpaikan optiot kuten :data-cy, :disabled ja :luokka."]
     [napit/nappi "Esimerkkitoiminto" #(reset! suoritettu? true)
      {:data-cy (kehys/ankkuri komponentti "-painike")
       :luokka "nappi-toissijainen"}]
     [:p {:data-cy (kehys/ankkuri komponentti "-tila")}
      (if @suoritettu? "Toiminto suoritettu" "Toimintoa ei ole suoritettu")]]))

(def ^:private nappi-variantit
  {:ensisijainen "nappi-ensisijainen"
   :toissijainen "nappi-toissijainen"
   :reunaton "nappi-reunaton"
   :myonteinen "nappi-myonteinen"
   :kielteinen "nappi-kielteinen"
   :varoitus "nappi-varoitus"})

(defn- tekstiarvo
  "Syötteen arvo vain, jos se on merkkijono. Muuten oletus, jotta syöte ei tuo markupia."
  [arvo oletus]
  (if (string? arvo) arvo oletus))

(defn- nappi-simulaattorissa [solmu tila _lapsi-fn]
  (let [{:keys [teksti variantti disabled tallennus-kaynnissa? data-cy]} (:asetukset solmu)]
    [napit/nappi
     (tekstiarvo teksti "")
     (fn [] (reset! (:tila tila) :onnistui))
     {:luokka (get nappi-variantit variantti (nappi-variantit :ensisijainen))
      :disabled (true? disabled)
      :tallennus-kaynnissa? (true? tallennus-kaynnissa?)
      :data-cy (tekstiarvo data-cy "ui-simulaattori-nappi")}]))

(defn- ikoni-ja-teksti [komponentti]
  [kehys/kortti komponentti
   [:p "Ikoni ja teksti vierekkäin. Ikoni on ensimmäinen argumentti, teksti toinen."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-info-sign] "Esimerkkiteksti"]]])

(defn- linkki [komponentti]
  (r/with-let [klikkaukset (r/atom 0)]
    (let [ankkuri (kehys/ankkuri komponentti)]
      [kehys/kortti komponentti
       [:p "Klikattava teksti. Käyttöpaikka antaa toiminnon, disabloitu linkki ei ole klikattava."]
       [:div {:data-cy (str ankkuri "-juuri")}
        [yleiset/linkki "Klikkaa minua" #(swap! klikkaukset inc) {:luokka "linkki" :block? true}]
        [yleiset/linkki "Disabloitu" #(swap! klikkaukset inc) {:disabloitu? true :block? true}]]
       [:p {:data-cy (str ankkuri "-arvo")}
        (str "Klikkauksia: " @klikkaukset)]])))

(defn- murupolku [komponentti]
  [kehys/kortti komponentti
   [:p "Sovelluksen yläosan navigaatio. Se renderöidään kerran harja.views.main-näkymässä, joten esikatselu ei renderöi toista instanssia."]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [:p "Katso murupolku sivun yläreunasta."]]])

(defn- ikoni-ja-teksti-simulaattorissa [solmu _tila _lapsi-fn]
  [:div {:data-cy "ui-simulaattori-ikoni-ja-teksti"}
   [ikonit/ikoni-ja-teksti [ikonit/livicon-info-sign]
    (tekstiarvo (get-in solmu [:asetukset :teksti]) "")]])

(defn- linkki-simulaattorissa-sisalto [otsikko disabloitu?]
  (r/with-let [klikkaukset (r/atom 0)]
    [:div {:data-cy "ui-simulaattori-linkki"}
     [yleiset/linkki otsikko #(swap! klikkaukset inc) {:disabloitu? disabloitu?}]
     [:p (str "Klikkauksia: " @klikkaukset)]]))

(defn- linkki-simulaattorissa [solmu _tila _lapsi-fn]
  (let [{:keys [otsikko disabloitu?]} (:asetukset solmu)]
    [linkki-simulaattorissa-sisalto (tekstiarvo otsikko "") (true? disabloitu?)]))

(register! :nappi {:esikatselu nappi
                   :renderoi nappi-simulaattorissa})
(register! :ikoni-ja-teksti {:esikatselu ikoni-ja-teksti
                             :renderoi ikoni-ja-teksti-simulaattorissa})
;; Globaali singleton (harja.views.main): ei renderöijää, ks. rajaukset.
(register! :murupolku {:esikatselu murupolku})
(register! :linkki {:esikatselu linkki
                    :renderoi linkki-simulaattorissa})
