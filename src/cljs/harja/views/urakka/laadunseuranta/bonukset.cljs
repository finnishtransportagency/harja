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
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.debug :as debug]

            [harja.views.urakka.toteumat.erilliskustannukset :as ek]

            [harja.tyokalut.tuck :as tuck-apurit]))

(defn- reversoi-mappi
  [mappi]
  (into {}
    (map (fn [[avain arvo]]
           [arvo avain]))
    mappi))

(def konversioavaimet
  {:perintapvm :pvm
   :summa :rahasumma
   :perustelu :lisatieto
   :laji :tyyppi
   :indeksi :indeksin_nimi
   :kasittelyaika :laskutuskuukausi})

(def valitut-avaimet [:pvm :rahasumma :toimenpideinstanssi :tyyppi :lisatieto :indeksikorjaus :sijainti
                      :laskutuskuukausi :kasittelytapa :indeksin_nimi :id :suorasanktio])

(defn- kannasta->lomake
  [avain]
  (or (get konversioavaimet avain)    
    avain))

(defn- keywordisoi
  [avain tiedot]
  (let [keywordisoi? (some? (get #{:laji :kasittelytapa} avain))]
    (println avain tiedot keywordisoi?)
    (if keywordisoi?
      (keyword tiedot)
      tiedot)))

(defn- assoc-in-muotoon
  [[avain tiedot]]
  (let [konversiot {:sijainti [:laatupoikkeama :sijainti]
                    :kasittelyaika [:laatupoikkeama :paatos :kasittelyaika]
                    :perustelu [:laatupoikkeama :paatos :perustelu]}]
    (println "aim" avain tiedot (or (get konversiot avain) [avain]))

    [(or (get konversiot avain) [avain]) tiedot]))

(defn- lomake->sanktiolistaus
  [lomake]
  (let [konversiot (reversoi-mappi konversioavaimet)
        tee-valitut-avaimet (map #(-> [% (get lomake %)]))
        keywordisoi-tiedot (map #(let [[avain tiedot] %]
                                   [avain (keywordisoi avain tiedot)]))
        konvertoi-avaimet (map #(let [[avain tiedot] %]
                                 
                                  [(or (get konversiot avain) avain) tiedot]))        
        assoc-in-avaimet (map assoc-in-muotoon)]
    (merge
      (reduce
        (fn [acc [avain tieto]]
          (println avain tieto acc)
          (assoc-in acc avain tieto))
        {}
        (into [] (comp tee-valitut-avaimet konvertoi-avaimet keywordisoi-tiedot assoc-in-avaimet) valitut-avaimet))
      {:bonus true :suorasanktio true})
    #_(reduce (fn [acc [avain tiedot]]
              (let [avain (or (get konversiot avain) avain)
                    vec-avain (assoc-in-muotoon avain)]
                (assoc-in acc vec-avain (keywordisoi avain tiedot))))
      {} lomake)))

(defn- bonus->lomake
  ([bonus]
   (bonus->lomake bonus {}))
  ([bonus acc]
   (reduce (fn [acc [avain tiedot]]
             (cond
               (map? tiedot)
               (bonus->lomake tiedot acc)

               (and
                 (some? tiedot)
                 (nil? (get acc avain))
                 (not= avain (kannasta->lomake avain)))
               (assoc acc (kannasta->lomake avain) tiedot)

               (and (some? tiedot)
                 (nil? (get acc avain)))
               (assoc acc avain tiedot)
               
               :else
               acc))
     acc bonus)))

(defrecord LisaaLiite [liite])
(defrecord PoistaLisattyLiite [])
(defrecord PoistaTallennettuLiite [liite])
(defrecord PaivitaLomaketta [lomake])
(defrecord TallennaBonus [])
(defrecord PoistaBonus [])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])
(defrecord TyhjennaLomake [sulje-fn])

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
  TyhjennaLomake
  (process-event
    [{sulje-fn :sulje-fn} {:keys [tallennettu-lomake] :as app}]
    (sulje-fn (lomake->sanktiolistaus tallennettu-lomake))
    (-> app
      (dissoc :lomake)
      (assoc :voi-sulkea? false)))
  PaivitaLomaketta
  (process-event
    [{lomake :lomake} app]
    (println "paivita" lomake)
    (let [{viimeksi-muokattu ::lomake/viimeksi-muokattu-kentta
           muokatut ::lomake/muokatut} lomake
          pvm-muokattu-viimeksi? (= :pvm viimeksi-muokattu)
          laskutuskuukausi-muokattuihin? (and pvm-muokattu-viimeksi?
                                           (nil? (:laskutuskuukausi muokatut))
                                           (some? (:laskutuskuukausi lomake)))
          lomake (if laskutuskuukausi-muokattuihin?
                   (update lomake ::lomake/muokatut conj :laskutuskuukausi)
                   lomake)]
      (assoc app :lomake lomake)))
  TallennusOnnistui
  (process-event    
    [{vastaus :vastaus} app]
    (println "succ" vastaus)
    (assoc app :tallennus-kaynnissa? false :voi-sulkea? true :tallennettu-lomake vastaus))
  TallennusEpaonnistui
  (process-event
    [{vastaus :vastaus} app]
    (println "fail" vastaus)
    (assoc app :tallennus-kaynnissa? false))
  PoistaBonus
  (process-event
    [_ app]
    (let [lomakkeen-tiedot (select-keys (:lomake app) valitut-avaimet)
          payload (assoc lomakkeen-tiedot
            :poistettu true
            :urakka-id (:id @nav/valittu-urakka)
            :tyyppi (-> lomakkeen-tiedot :tyyppi name)
            :palauta-tallennettu? true)]
      (-> app
        (tuck-apurit/post! :tallenna-erilliskustannus
               payload
               {:onnistui ->TallennusOnnistui
                :epaonnistui ->TallennusEpaonnistui})
        (assoc :tallennus-kaynnissa? true))))
  TallennaBonus
  (process-event
    [_ app]    
    (let [lomakkeen-tiedot (select-keys (:lomake app) valitut-avaimet)
          payload (merge lomakkeen-tiedot
                    {:urakka-id (:id @nav/valittu-urakka)
                     :tyyppi (-> lomakkeen-tiedot :tyyppi name)
                     :palauta-tallennettu? true})]
      (println "save!" payload)
      (-> app
        (tuck-apurit/post! :tallenna-erilliskustannus
               payload
               {:onnistui ->TallennusOnnistui
                :epaonnistui ->TallennusEpaonnistui})
        (assoc :tallennus-kaynnissa? true)))))

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

(defn- hae-tpi-idlla
  [tpi-id]
  (some
    #(when (= tpi-id (:tpi_id %))
       %)
    @tiedot-urakka/urakan-toimenpideinstanssit))

(defn bonukset-lomake
  [sulje-fn e! app]
  (let [{lomakkeen-tiedot :lomake uusi-liite :uusi-liite voi-sulkea? :voi-sulkea?} app
        urakka-id (:id @nav/valittu-urakka)
        laskutuskuukaudet (pyorayta-laskutuskuukausi-valinnat)]
    (when voi-sulkea? (e! (->TyhjennaLomake sulje-fn)))
    [:<>
     [:h2 "Bonukset"]
     [debug/debug lomakkeen-tiedot]
     [lomake/lomake
      {:otsikko "Bonuksen tiedot"
       :ei-borderia? true
       :vayla-tyyli? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :luokka "padding-16 taustavari-taso3"
       :validoi-alussa? false
       :voi-muokata? true
       :muokkaa! #(e! (->PaivitaLomaketta %))
       :footer-fn (fn [bonus]
                    [:<>
                     [:span.nappiwrappi.flex-row
                      [napit/yleinen-ensisijainen "Tallenna" #(e! (->TallennaBonus))]
                      (when (:id lomakkeen-tiedot)
                        [napit/kielteinen "Poista" (fn [_]
                                                     (varmista-kayttajalta/varmista-kayttajalta
                                                       {:otsikko "Bonuksen poistaminen"
                                                        :sisalto "Haluatko varmasti poistaa bonuksen? Toimintoa ei voi perua."
                                                        :modal-luokka "varmistus-modal"
                                                        :hyvaksy "Poista"
                                                        :toiminto-fn #(e! (->PoistaBonus))}))
                         {:luokka "oikealle"}])
                      [napit/peruuta "Sulje" #(e! (->TyhjennaLomake sulje-fn))]]])}
      [(let [hae-tpin-tiedot (comp hae-tpi-idlla :toimenpideinstanssi)
             tpi (hae-tpin-tiedot lomakkeen-tiedot)]
           {:otsikko "Bonus"
            :nimi :tyyppi
            :tyyppi :valinta
            :pakollinen? true
            :valinnat (ek/luo-kustannustyypit (:tyyppi @nav/valittu-urakka) (:id @istunto/kayttaja) tpi)
            :valinta-nayta ek/erilliskustannustyypin-teksti
            ::lomake/col-luokka "col-xs-12"})
       {:otsikko "Perustelu"
        :nimi :lisatieto
        :tyyppi :text
        :pakollinen? true
        ::lomake/col-luokka "col-xs-12"}
       {:otsikko "Kulun kohdistus"
        :nimi :toimenpideinstanssi
        :tyyppi :valinta
        :pakollinen? true
        :valitse-oletus? true
        :valinta-arvo :tpi_id
        :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
        :valinnat (filter #(= "23150" (:t2_koodi %)) @tiedot-urakka/urakan-toimenpideinstanssit)
        ::lomake/col-luokka "col-xs-12"
        :disabled? true}
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
          :aseta (fn [rivi arvo & muut]
                   (println rivi arvo (keys rivi) muut)
                   (cond-> rivi
                     (nil? (:laskutuskuukausi rivi)) (assoc :laskutuskuukausi
                                                       (some #(when (and
                                                                      (= (:kuukausi %) (pvm/kuukausi arvo))
                                                                      (= (:vuosi %) (pvm/vuosi arvo)))
                                                                (:pvm %))
                                                         laskutuskuukaudet))
                     true (assoc :pvm arvo)))}
         {:otsikko "Laskutuskuukausi"
          :nimi :laskutuskuukausi
          :tyyppi :valinta
          :valinnat laskutuskuukaudet
          :valinta-arvo :pvm
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

(defn bonukset*
  [auki? avattu-bonus haetut-sanktiot]
  (let [sulje-fn #(do
                    (println "bonus sulje" %)
                    (when (some? (:id %))
                      (println "swapping with" %)
                      (if (some (fn [rivi] (= (:id rivi) (:id %))) @haetut-sanktiot)
                        (swap! haetut-sanktiot (fn [sanktiolistaus]
                                                 (mapv (fn [rivi]
                                                         (if (= (:id rivi) (:id %))
                                                           %
                                                           rivi))
                                                   sanktiolistaus)))
                        (swap! haetut-sanktiot conj %)))
                    (reset! auki? false))
        bonukset-tila (r/atom {:lomake (or
                                         (when (some? (:id avattu-bonus))
                                           (bonus->lomake avattu-bonus))
                                         {})})]
    (fn [_ _]
      [tuck/tuck bonukset-tila (r/partial bonukset-lomake sulje-fn)])))
