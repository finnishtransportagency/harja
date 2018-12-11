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
            [harja.ui.napit :as napit]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn- hakulomake [urakka sidonta-kaynnissa?]
  [lomake/lomake {:otsikko "Urakan tiedot"
                  :muokkaa! (fn [uusi-data]
                              (reset! yha/hakulomake-data uusi-data))
                  :footer [napit/palvelinkutsu-nappi
                           "Hae"
                           #(yha/hae-yha-urakat (merge {:harja-urakka-id (:id urakka)} @yha/hakulomake-data))
                           {:luokka "nappi-ensisijainen"
                            :disabled sidonta-kaynnissa?
                            :virheviesti "Urakoiden haku YHA:sta epäonnistui."
                            :kun-virhe (fn [_]
                                         (reset! yha/hakutulokset-data []))
                            :kun-onnistuu (fn [vastaus]
                                            (log "[YHA] YHA-urakat haettu onnistuneesti: " (pr-str vastaus))
                                            (reset! yha/hakutulokset-data vastaus))}]}
   [{:otsikko "YHA-nimi"
     :nimi :yhatunniste
     :pituus-max 512
     :tyyppi :string}
    {:otsikko "Vuosi"
     :nimi :vuosi
     :pituus-max 512
     :tyyppi :positiivinen-numero}]
   @yha/hakulomake-data])

(defn- hakutulokset [urakka sidonta-kaynnissa?]
  (let [tulokset @yha/hakutulokset-data
        ur-id @nav/valittu-urakka-id]
    [grid
     {:otsikko "Löytyneet urakat"
      :tyhja (if (nil? tulokset) [ajax-loader "Haetaan urakoita..."] "Urakoita ei löytynyt")
      :tunniste #((juxt :yhatunnus :elyt :vuosi) %)}
     [{:otsikko "YHA-nimi"
       :nimi :yhatunnus
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
                        [:span (str "Sidottu jo Harjan urakkaan: " (:sidottu-urakkaan rivi))]
                        [napit/palvelinkutsu-nappi
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
                          :virheviesti "Urakan sidonta epäonnistui."
                          :kun-onnistuu (fn [vastaus]
                                          (log "[YHA] Liiteteään yhatiedot.")
                                          (nav/paivita-urakan-tiedot! ur-id assoc :yhatiedot vastaus)
                                          (modal/piilota!)
                                          (log "[YHA] Aloitetaan kohteiden haku ja käsittely.")
                                          (yha/paivita-yha-kohteet (:id urakka) {:nayta-ilmoitus-ei-uusia-kohteita? false}))}]))}]
     (sort-by :yhatunnus tulokset)]))

(defn- sidonta-kaynnissa []
  [ajax-loader "Sidonta käynnissä..."])

(defn- tuontidialogi* [urakka sidonta-kaynnissa? optiot]
  [:div
   (if (:sitomaton-urakka? optiot)
     [:div
      [:p (str (:nimi urakka) " täytyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan.
       Etsi YHA-urakka täyttämällä vähintään yksi hakuehto ja tee sidonta.")]
      [:p "Varmista, että YHA:an luotu kohdeluettelo on valmis ennen kuin teet sidonnan.
       Harjaan kerran tuotuja kohteita ei päivitetä enää sidonnan jälkeen,
       mutta uusia YHA-kohteita voidaan tuoda sidottuun Harja-urakkaan.
       Kun sidonta on valmis, voi sidonnan vaihtaa niin kauan kuin urakan tietoja ei ole muokattu."]]
     [lomake/yleinen-varoitus (str (:nimi urakka) " on jo sidottu YHA-urakkaan " (get-in urakka [:yhatiedot :yhatunnus]) ". Jos vaihdat sidonnan toiseen urakkaan, kaikki Harja-urakkaan tuodut kohteet ja niiden ilmoitukset poistetaan.")])
   [hakulomake urakka sidonta-kaynnissa?]
   [hakutulokset urakka sidonta-kaynnissa?]
   (when sidonta-kaynnissa?
     [sidonta-kaynnissa])])

(defn- tuontidialogi [urakka optiot]
  (let [urakkalla-sopimuksia? (not (empty? (:sopimukset urakka)))
        sidonta-kaynnissa? @yha/sidonta-kaynnissa?]
    (if urakkalla-sopimuksia?
      [tuontidialogi* urakka sidonta-kaynnissa? optiot]
      [:div
       [:p (str (:nimi urakka) " täytyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan.
      Urakalla ei kuitenkaan ole yhtään sopimusta Harjassa, joten sidontaa ei voi tehdä.
      Urakan sopimus pitää perustaa Sampoon.")]])))

(defn nayta-tuontidialogi [urakka]
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"
     :luokka "yha-tuonti"
     :footer [napit/sulje #(modal/piilota!)]
     :sulje #(reset! yha/hakutulokset-data [])}
    [tuontidialogi urakka {:sitomaton-urakka? (nil? (:yhatiedot urakka))}]))
