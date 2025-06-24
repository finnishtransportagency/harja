(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
  hae-kiintea-kustannus-kuukausittain poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukaudelta<! paivita-kiinteat-kustannukset-kuukausittain<!
  hae-erillishankinta-kuukausittain hae-kuukauden-erillishankinta
  paivita-kuukauden-erillishankinta<! tallenna-kuukauden-erillishankinta<!
  hae-hoidonjohtopalkkiot-kuukausittain hae-kuukauden-hoidonjohtopalkkio
  hae-rahavaraus-vuodelta
  paivita-kuukauden-hoidonjohtopalkkio<! tallenna-kuukauden-hoidonjohtopalkkio<!
  vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille!
  vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille!
  indeksikorjaukset-vahvistettu? paivita-tavoite-ja-kattohinta<!
  lisaa-tavoite-ja-kattohinta<! hae-urakan-hoitovuoden-tavoitetiedot)

(defn hae-kiinteat-kustannukset [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Haetaan urakan toimenpiteet
        toimenpiteet (hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        ;; Kiinteähintaiset kustannukset
        kiinteat (reduce (fn [acc {:keys [nimi toimenpideinstanssi-id] :as toimenpide}]
                           (let [kiinteat (hae-kiintea-kustannus-kuukausittain
                                            db {:sopimus-id sopimus-id
                                                :vuosi hoitovuoden-alkuvuosi
                                                :toimenpideinstanssi-id toimenpideinstanssi-id})
                                 kiinteat-alkukausi (filter #(>= (:kuukausi %) 10) kiinteat)
                                 kiinteat-loppukausi (filter #(<= (:kuukausi %) 9) kiinteat)
                                 alkukausi (if (seq kiinteat-alkukausi) (apply + (map :summa kiinteat-alkukausi)) 0)
                                 alkukausi-indeksikorjattu (if (seq kiinteat-alkukausi)
                                                             (apply + (map (fn [rivi]
                                                                             (if (:summa_indeksikorjattu rivi)
                                                                               (:summa_indeksikorjattu rivi)
                                                                               0))
                                                                        kiinteat-alkukausi))
                                                             0)
                                 loppukausi (if (seq kiinteat-loppukausi) (apply + (map :summa kiinteat-loppukausi)) 0)
                                 loppukausi-indeksikorjattu (if (seq kiinteat-loppukausi)
                                                              (apply + (map (fn [rivi]
                                                                              (if (:summa_indeksikorjattu rivi)
                                                                                (:summa_indeksikorjattu rivi)
                                                                                0))
                                                                         kiinteat-loppukausi))
                                                              0)]
                             (conj acc {:nimi nimi
                                        :toimenpideinstanssi-id toimenpideinstanssi-id
                                        :alkukausi alkukausi
                                        :alkukausi-indeksikorjattu alkukausi-indeksikorjattu
                                        :loppukausi loppukausi
                                        :loppukausi-indeksikorjattu loppukausi-indeksikorjattu
                                        :yhteensa (+ alkukausi loppukausi)
                                        :yhteensa-indeksikorjattu (+ alkukausi-indeksikorjattu loppukausi-indeksikorjattu)
                                        :pysyvat-muutokset "Ei muutoksia"})))
                   []
                   toimenpiteet)
        ;; Yhteenvetorivi
        yhteenveto {:nimi "Yhteensä"
                    :alkukausi (apply + (map :alkukausi kiinteat))
                    :alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu kiinteat))
                    :loppukausi (apply + (map :loppukausi kiinteat))
                    :loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu kiinteat))
                    :yhteensa (+ (apply + (map :alkukausi kiinteat)) (apply + (map :loppukausi kiinteat)))
                    :yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu kiinteat)) (apply + (map :loppukausi-indeksikorjattu kiinteat)))
                    :pysyvat-muutokset "Ei muutoksia"}
        kiinteat (conj kiinteat yhteenveto)]
    kiinteat))

(defn hae-rahavaraukset
  "Haetaan rahavaraukset tietokannsta kustannussuunnitelmaan.
  Saadaan [{:nimi <rahavarausnimi> :summa <summa> :summa-indeksikorjattu nil} ...]"
  [db sopimus-id hoitovuoden-alkuvuosi]
  (let [rahavaraukset (hae-rahavaraus-vuodelta db {:sopimus-id sopimus-id :vuosi hoitovuoden-alkuvuosi})]
    rahavaraukset))

(defn hae-hoidonjohtopalkkiot [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        hoidonjohtopalkkiot (when hoidonjohto-tpi-id
                              (hae-hoidonjohtopalkkiot-kuukausittain db
                                {:sopimus-id sopimus-id
                                 :vuosi hoitovuoden-alkuvuosi
                                 :tehtava-id (:id tehtava)
                                 :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        hoidonjohtopalkkiot (if (seq hoidonjohtopalkkiot)
                              ;; Jos on tallennettu jo hoidonjohtopalkkioita, niin lisätään niihin kalenterikuukausi
                              (map (fn [rivi]
                                     (merge rivi
                                       {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                             (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)}))
                                hoidonjohtopalkkiot)
                              ;; Jos ei ole tallennettu hoidonjohtopalkkioita, niin luodaan nolla arvot
                              (mapv (fn [kk]
                                      (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                        {:id nil
                                         :sopimus sopimus-id
                                         :tehtava (:id tehtava)
                                         :toimenpideinstanssi hoidonjohto-tpi-id
                                         :kuukausi kk
                                         :vuosi vuosi
                                         :summa 0
                                         :summa_indeksikorjattu nil
                                         :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)}))
                                [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) hoidonjohtopalkkiot)))

(defn hae-erillishankinnat [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        erillishankinnat (when hoidonjohto-tpi-id
                           (hae-erillishankinta-kuukausittain db
                             {:sopimus-id sopimus-id
                              :vuosi hoitovuoden-alkuvuosi
                              :tehtavaryhma-id (:id tehtavaryhma)
                              :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        erillishankinnat (if (seq erillishankinnat)
                           ;; Jos on tallennettu jo erillishankintoja, niin lisätään niihin kalenterikuukausi
                           (map (fn [rivi]
                                  (merge rivi
                                    {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                          (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)}))
                             erillishankinnat)
                           ;; Jos ei ole tallennettu erillishankintoja, niin luodaan nolla arvot
                           (mapv (fn [kk]
                                   (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                     {:id nil
                                      :sopimus sopimus-id
                                      :tehtavaryhma (:id tehtavaryhma)
                                      :toimenpideinstanssi hoidonjohto-tpi-id
                                      :kuukausi kk
                                      :vuosi vuosi
                                      :summa 0
                                      :summa_indeksikorjattu nil
                                      :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)}))
                             [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) erillishankinnat)))

(defn paivita-tavoite-ja-kattohinta
  "Jokaisen kustannussuunnitelman muutoksen jälkeen tavoite- ja kattohinta pitää laskea uusiksi."
  [db kayttaja urakka-id hoitovuoden-alkuvuosi]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitokausinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))

        ;; Kaikki kustannussuunnitelman summat vaikuttaa tavoitehintaan
        ;; Pysyvät muutokset lisätään mukaan joko vähentämään tai lisäämään tavoitehintaa
        kilpailutettavat-hankinnat (hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        hankinnat-yht (:yhteensa (last kilpailutettavat-hankinnat))

        rahavaraukset (hae-rahavaraukset db sopimus-id hoitovuoden-alkuvuosi)
        rahavaraukset-yht (apply + (map (fn [rivi] (:summa rivi 0)) rahavaraukset))

        erillishankinnat (hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        erillishankinnat-yht (apply + (map (fn [rivi] (:summa rivi 0)) erillishankinnat))

        hoidonjohtopalkkiot (hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        hoidonjohtopalkkiot-yht (apply + (map (fn [rivi] (:summa rivi 0)) hoidonjohtopalkkiot))
        ;; TODO: kun muutokset on valmiita, niin hae tiedot
        pysyvat-muutokset-maara 0

        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        hoitovuoden-alun-tavoitehinta (+ hankinnat-yht rahavaraukset-yht erillishankinnat-yht hoidonjohtopalkkiot-yht)

        hoitovuoden-alun-kattohinta (or (when kattohintakerroin
                                          (* kattohintakerroin hoitovuoden-alun-tavoitehinta)) 0)

        tavoitetiedot (first (hae-urakan-hoitovuoden-tavoitetiedot db {:hoitokausinumero hoitokausinumero
                                                                       :urakka-id urakka-id}))

        ;; Päivitä tai lisää tavoite ja kattohinta tietokantaan
        tiedot (if (:id tavoitetiedot)
                 (paivita-tavoite-ja-kattohinta<! db
                   {:urakka-id urakka-id
                    :hoitokausinumero hoitokausinumero
                    :tavoitehinta hoitovuoden-alun-tavoitehinta
                    :kattohinta hoitovuoden-alun-kattohinta
                    :muokkaaja (:id kayttaja)})
                 (lisaa-tavoite-ja-kattohinta<! db
                   {:urakka-id urakka-id
                    :hoitokausinumero hoitokausinumero
                    :tavoitehinta hoitovuoden-alun-tavoitehinta
                    :kattohinta hoitovuoden-alun-kattohinta
                    :luoja (:id kayttaja)}))]
    tiedot))

(defn tallenna-hankintojen-kuukausittainen-summa [db kk-jakso alkujakso? nimi viimeinen-summa summa hoitovuoden-alkuvuosi sopimus-id
                                                  toimenpideinstanssi-id kayttaja-id]
  (let [_ (doseq [kk kk-jakso]
            (let [summa (cond
                          (and alkujakso? (= kk 12)) viimeinen-summa
                          (and (not alkujakso?) (= kk 9)) viimeinen-summa
                          :else summa)
                  dbrivi (first (hae-kiintea-kustannus-toimenpiteelle-kuukaudelta db
                                  {:vuosi hoitovuoden-alkuvuosi
                                   :kuukausi kk
                                   :sopimus-id sopimus-id
                                   :toimenpideinstanssi-id toimenpideinstanssi-id}))
                  t (if (:id dbrivi)
                      (paivita-kiinteat-kustannukset-kuukausittain<! db
                        {:id (:id dbrivi)
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu nil
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :tehtavaryhma nil
                         :tehtava nil
                         :muokkaaja kayttaja-id})
                      ;; Lisää uusi
                      (tallenna-kiinteat-kustannukset-kuukaudelta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu nil
                         :tehtavaryhma nil
                         :tehtava nil
                         :luoja kayttaja-id}))]))]))

(defn tallenna-kilpailutettavat-hankinnat
  [db kayttaja urakka-id hoitovuoden-alkuvuosi kilpailutettavat-hankinnat]
  ;; Lisätään transktiot, jottei yhden epäonnistuminen päästä muita läpi
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ; Splittaa alkukauden summat kuukausittain
        _ (doseq [{:keys [nimi alkukausi loppukausi toimenpideinstanssi-id] :as toimenpide} kilpailutettavat-hankinnat]
            (let [alkukausi (bigdec alkukausi)

                  alkukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ alkukausi 3)))
                  alkukausi-viimeinen-kuukausi (- alkukausi (* 2 alkukausi-kuukaudet))
                  loppukausi (bigdec loppukausi)
                  loppukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ loppukausi 9)))
                  loppukausi-viimeinen-kuukausi (- loppukausi (* 8 loppukausi-kuukaudet))
                  ;; Tallenna alkujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 10 13) true nimi alkukausi-viimeinen-kuukausi alkukausi-kuukaudet
                      hoitovuoden-alkuvuosi sopimus-id toimenpideinstanssi-id (:id kayttaja))
                  ;; Tallenna loppujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 1 10) false nimi loppukausi-viimeinen-kuukausi loppukausi-kuukaudet
                      (inc hoitovuoden-alkuvuosi) sopimus-id toimenpideinstanssi-id (:id kayttaja))]))]))

(defn tallenna-erillishankinnat
  [db kayttaja urakka-id hoitovuoden-alkuvuosi erillishankinnat]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db
                              {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        ; Tallenna kuukausittaiset summat
        _ (doseq [rivi erillishankinnat]
            (let [dbrivi (first (hae-kuukauden-erillishankinta db {:id (:id rivi)}))
                  t (if (:id dbrivi)
                      (paivita-kuukauden-erillishankinta<! db
                        {:id (:id dbrivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-erillishankinta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi (:vuosi rivi)
                         :kuukausi (:kuukausi rivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :tehtavaryhma-id (:id tehtavaryhma)
                         :luoja (:id kayttaja)}))]))]))

(defn tallenna-hoidonjohtopalkkiot
  [db kayttaja urakka-id hoitovuoden-alkuvuosi hoidonjohtopalkkiot]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        ; Tallenna kuukausittaiset summat
        _ (doseq [rivi hoidonjohtopalkkiot]
            (let [dbrivi (first (hae-kuukauden-hoidonjohtopalkkio db {:id (:id rivi)}))
                  t (if (:id dbrivi)
                      (paivita-kuukauden-hoidonjohtopalkkio<! db
                        {:id (:id dbrivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-hoidonjohtopalkkio<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi (:vuosi rivi)
                         :kuukausi (:kuukausi rivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :tehtava-id (:id tehtava)
                         :luoja (:id kayttaja)}))]))]))
