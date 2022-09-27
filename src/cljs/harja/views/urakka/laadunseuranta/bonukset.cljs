(ns harja.views.urakka.laadunseuranta.bonukset
  "Bonuksien käsittely ja luonti"
  (:require [reagent.core :as r]
            [tuck.core :as tuck]

            [harja.pvm :as pvm]
            
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.istunto :as istunto]
            
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.liitteet :as liitteet]

            [harja.views.urakka.toteumat.erilliskustannukset :as ek]

            [harja.tyokalut.tuck :as tuck-apurit]))

(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite])
(defrecord PaivitaLomaketta [lomake])
(defrecord TallennaBonus [])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])

(extend-protocol tuck/Event
  PoistaLisattyLiite
  (process-event
    [_ app]
    (assoc-in app [:uusi-liite] nil))
  PoistaTallennettuLiite
  (process-event
    [{liite :liite} app])
  LisaaLiite
  (process-event
    [{liite :liite} app]
    (assoc-in app [:uusi-liite] liite))
  PaivitaLomaketta
  (process-event
    [{lomake :lomake} app]
    (println "paivita" lomake)
    (assoc app :lomake lomake))
  TallennusOnnistui
  (process-event    
    [vastaus app]
    (println "succ" vastaus)
    app)
  TallennusEpaonnistui
  (process-event
    [vastaus app]
    (println "fail" vastaus)
    app)
  TallennaBonus
  (process-event
    [_ app]    
    (let [lomakkeen-tiedot (select-keys (:lomake app) [:pvm :rahasumma :toimenpideinstanssi :tyyppi :lisatieto
                                                       :laskutuskuukausi :kasittelytapa :indeksin_nimi])
          payload (merge lomakkeen-tiedot
                    {:urakka-id (:id @nav/valittu-urakka)
                     :tyyppi (-> lomakkeen-tiedot :tyyppi name)})]
      (println "save!" payload)
      (tuck-apurit/post! :tallenna-erilliskustannus
          payload
          {:onnistui ->TallennusOnnistui
           :epaonnistui ->TallennusEpaonnistui})
      app)))

(defn pyorayta-laskutuskuukausi-valinnat
  []
  (let [{:keys [alkupvm loppupvm]} @nav/valittu-urakka
        vuodet (range (pvm/vuosi alkupvm) (pvm/vuosi loppupvm))]
    (into []
      (sort-by (juxt :vuosi :kuukausi)
        (mapcat (fn [vuosi]
                  (let [kuukaudet (range 1 13)
                        inc-jos-tarvii (fn [kuukausi vuosi]
                                         (if (< kuukausi 10)
                                               (inc vuosi)
                                               vuosi))]
                    (into [] (map
                               (fn [kuukausi]
                                 {:pvm (pvm/->pvm (str "15." kuukausi "." (inc-jos-tarvii kuukausi vuosi)))
                                  :vuosi (inc-jos-tarvii kuukausi vuosi)
                                  :kuukausi kuukausi
                                  :teksti (str kuukausi "-" (inc-jos-tarvii kuukausi vuosi))}))
                      kuukaudet)))
          vuodet)))))

(defn bonukset-lomake
  [sulje-fn e! app]
  (let [{lomakkeen-tiedot :lomake uusi-liite :uusi-liite} app
        urakka-id (:id @nav/valittu-urakka)
        laskutuskuukaudet (pyorayta-laskutuskuukausi-valinnat)]
    [:<>
     [:h2
      "Bonukset"]
     [lomake/lomake
      {:otsikko "Bonuksen tiedot"
       :ei-borderia? true
       :vayla-tyyli? true
       :luokka "padding-16 taustavari-taso3"
       :validoi-alussa? false
       :voi-muokata? true
       :muokkaa! #(e! (->PaivitaLomaketta %))
       :footer-fn (fn [bonus]
                    [:<>
                     [:div (pr-str bonus)]
                     [:span.nappiwrappi.flex-row
                      [napit/yleinen-ensisijainen "Tallenna" #(e! (->TallennaBonus))]
                      [napit/kielteinen "Poista" #(println %)]
                      [napit/peruuta "Sulje" sulje-fn]]])}
      [{:otsikko "Bonus"
        :nimi :tyyppi
        :tyyppi :valinta
        :pakollinen? true
        :valinnat (ek/luo-kustannustyypit (:tyyppi @nav/valittu-urakka) (:id @istunto/kayttaja) (:toimenpideinstanssi lomakkeen-tiedot))
        ::lomake/col-luokka "col-xs-12"}
       {:otsikko "Perustelu"
        :nimi :lisatieto
        :tyyppi :text
        :pakollinen? true
        ::lomake/col-luokka "col-xs-12"}
       {:otsikko "Kulun kohdistus"
        :nimi :toimenpideinstanssi
        :tyyppi :valinta
        :pakollinen? true
        :valinta-arvo :tpi_id
        :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
        :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
        ::lomake/col-luokka "col-xs-12"}
       (lomake/ryhma
         {:rivi? true}
         {:otsikko "Summa"
          :nimi :rahasumma
          :tyyppi :positiivinen-numero
          :pakollinen? true
          ::lomake/col-luokka "col-xs-4"
          :yksikko "€"}
         {:otsikko "Indeksi"
          :nimi :indeksin_nimi
          :tyyppi :valinta
          :disabled? true
          ::lomake/col-luokka "col-xs-4"
          :valinnat ["Ei"]})
       (lomake/ryhma
         {:rivi? true}
         {:otsikko "Käsitelty"
          :nimi :pvm
          :tyyppi :pvm
          :pakollinen? true
          ::lomake/col-luokka "col-xs-4"
          :aseta (fn [rivi arvo]
                   (println rivi arvo (keys rivi))
                   (cond-> rivi
                     (nil? (:laskutuskuukausi rivi)) (assoc :laskutuskuukausi
                                                       (some #(when (and
                                                                      (= (:kuukausi %) (pvm/kuukausi arvo))
                                                                      (= (:vuosi %) (pvm/vuosi arvo)))
                                                                 %)
                                                         laskutuskuukaudet))
                     true (assoc :pvm arvo)))}
         {:otsikko "Laskutuskuukausi"
          :nimi :laskutuskuukausi
          :tyyppi :valinta
          :valinnat laskutuskuukaudet
         ; :valinta-arvo :pvm
          :valinta-nayta :teksti
          :pakollinen? true
          ::lomake/col-luokka "col-xs-4"
          :hae :laskutuskuukausi})
       {:otsikko "Käsittelytapa"
        :nimi :kasittelytapa
        :tyyppi :valinta
        :pakollinen? true
        ::lomake/col-luokka "col-xs-12"
        :valinta-nayta #(if % (case %
                                :tyomaakokous "Työmaakokous"
                                :valikatselmus "Välikatselmus"
                                :puhelin "Puhelimitse"
                                :kommentit "Harja-kommenttien perusteella"
                                :muu "Muu tapa"
                                nil)
                            "- valitse käsittelytapa -")
        :valinnat sanktio-domain/kasittelytavat}       
       {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
        :tyyppi :komponentti
        ::lomake/col-luokka "col-xs-12"
        :komponentti (fn [_]
                       [liitteet/liitteet-ja-lisays urakka-id (get-in app [:liitteet])
                        {:uusi-liite-atom (r/wrap uusi-liite
                                            #(e! (->LisaaLiite %)))
                         :uusi-liite-teksti "Lisää liite"
                         :salli-poistaa-lisatty-liite? true
                         :poista-lisatty-liite-fn #(e! (->PoistaLisattyLiite))
                         :salli-poistaa-tallennettu-liite? true
                         :poista-tallennettu-liite-fn #(e! (->PoistaTallennettuLiite %))
                         #_(fn [liite-id]
                           (liitteet/poista-liite-kannasta
                             {:urakka-id urakka-id
                              :domain :laatupoikkeama
                              :domain-id (get-in @tiedot/valittu-sanktio [:laatupoikkeama :id])
                              :liite-id liite-id
                              :poistettu-fn (fn []
                                              (let [liitteet (get-in @muokattu [:laatupoikkeama :liitteet])]
                                                (swap! tiedot/valittu-sanktio assoc-in [:laatupoikkeama :liitteet]
                                                  (filter (fn [liite]
                                                            (not= (:id liite) liite-id))
                                                    liitteet))))}))}])}]
      lomakkeen-tiedot]]))

(defonce bonukset-tila (r/atom {:lomake {}}))

(defn bonukset*
  [auki?]
  (let [sulje-fn #(reset! auki? false)]
    [tuck/tuck bonukset-tila (r/partial bonukset-lomake sulje-fn)]))
