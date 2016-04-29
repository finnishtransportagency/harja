(ns harja.views.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.ui.grid :refer [grid]]
            [harja.tiedot.navigaatio :as nav]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn- hakulomake []
  [lomake/lomake {:otsikko "Urakan tiedot"
                  :muokkaa! (fn [uusi-data]
                              (reset! yha/hakulomake-data uusi-data))
                  :footer [harja.ui.napit/palvelinkutsu-nappi
                           "Hae"
                           #(yha/hae-yha-urakat yha/hakulomake-data)
                           {:luokka "nappi-ensisijainen"
                            :disabled @yha/sidonta-kaynnissa?
                            :kun-onnistuu (fn [vastaus]
                                            (log "YHA-urakat haettu onnistuneesti: " (pr-str vastaus))
                                            (reset! yha/hakutulokset-data vastaus))}]}
   [{:otsikko "YHA-tunniste"
     :nimi :yhatunniste
     :pituus-max 512
     :tyyppi :string}
    {:otsikko "Sampo-tunniste"
     :nimi :sampotunniste
     :pituus-max 512
     :tyyppi :string}
    {:otsikko "Vuosi"
     :nimi :vuosi
     :pituus-max 512
     :tyyppi :positiivinen-numero}]
   @yha/hakulomake-data])

(defn- hakutulokset [urakka]
  (let [sidonta-kaynnissa? @yha/sidonta-kaynnissa?]
    [grid
     {:otsikko "Löytyneet urakat"
      :tyhja (if (nil? @yha/hakutulokset-data) [ajax-loader "Haetaan urakoita..."] "Urakoita ei löytynyt")
      :tunniste :yhatunnus}
     [{:otsikko "Tunnus"
       :nimi :yhatunnus
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "Nimi"
       :nimi :yhanimi
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "ELY:t"
       :nimi :elyt
       :tyyppi :string
       :muokattava? (constantly false)
       :fmt #(str/join ", " %)}
      {:otsikko "Vuodet"
       :nimi :vuodet
       :tyyppi :string
       :muokattava? (constantly false)
       :fmt #(str/join ", " %)}
      {:otsikko "Sidonta"
       :nimi :valitse
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      (if (:sidottu-urakkaan rivi)
                        [:span (str "Sidottu Harja-urakkaan " (:sidottu-urakkaan rivi))]
                        [harja.ui.napit/palvelinkutsu-nappi
                         "Sido"
                         #(do
                           (log "[YHA] Sidotaan Harja-urakka " (:id urakka) " yha-urakkaan: " (pr-str rivi))
                           (reset! yha/sidonta-kaynnissa? true)
                           (yha/sido-yha-urakka-harja-urakkaan (:id urakka) rivi))
                         {:luokka "nappi-ensisijainen"
                          :disabled sidonta-kaynnissa?
                          :kun-valmis (fn [vastaus]
                                        (log "[YHA] Sidonta suoritettu, vastaus: " (pr-str vastaus))
                                        (reset! yha/sidonta-kaynnissa? false))
                          :kun-onnistuu (fn [vastaus]
                                          (swap! nav/valittu-urakka assoc :yhatiedot vastaus)
                                          (modal/piilota!))}]))}]
     @yha/hakutulokset-data]))

(defn- sidonta-kaynnissa []
  [ajax-loader "Sidonta käynnissä..."])

(defn- tuontidialogi [urakka optiot]
  [:div
   (if (:sitomaton-urakka? optiot)
     [vihje (str (:nimi urakka) " täytyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan. Etsi YHA-urakka täyttämällä vähintään yksi hakuehto ja tee sidonta.")]
     [lomake/yleinen-varoitus (str (:nimi urakka) " on jo sidottu YHA-urakkaan " (get-in urakka [:yhatiedot :yhanimi]) ". Jos vaihdat sidonnan toiseen urakkaan, kaikki Harja-urakkaan tuodut kohteet poistetaan.")])
   [hakulomake]
   [hakutulokset urakka]
   (when @yha/sidonta-kaynnissa?
     [sidonta-kaynnissa])])

(defn nayta-tuontidialogi [urakka]
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"
     :luokka "yha-tuonti"
     :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                      (.preventDefault e)
                                                      (modal/piilota!))}
              "Sulje"]
     :sulje #(reset! yha/hakutulokset-data [])}
    (tuontidialogi urakka {:sitomaton-urakka? (nil? (:yhatiedot urakka))})))