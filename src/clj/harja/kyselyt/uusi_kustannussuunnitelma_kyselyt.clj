(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.tyokalut.yleiset :refer [round2] :as yleiset]
            [harja.domain.mhu :as mhu]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as kust-domain]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.kustannusarvioidut-tyot :as ka-q]
            [harja.kyselyt.kiinteahintaiset-tyot :as kiint-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
  hae-kiintea-kustannus-kuukausittain poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukaudelta<! paivita-kiinteat-kustannukset-kuukausittain<!
  hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
  hae-erillishankinta-kuukausittain hae-kuukauden-erillishankinta
  paivita-kuukauden-erillishankinta<! tallenna-kuukauden-erillishankinta<!
  hae-viimeisin-muokkaaja-erillishankinnoille
  hae-hoidonjohtopalkkiot-kuukausittain hae-kuukauden-hoidonjohtopalkkio hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
  hae-rahavaraus-vuodelta
  paivita-kuukauden-hoidonjohtopalkkio<! tallenna-kuukauden-hoidonjohtopalkkio<!
  hae-johto-ja-hallintokorvaukset-kuukausittain
  hae-johto-ja-hallintokorvaukset-2019-mhu
  hae-kuukauden-johto-ja-hallintokorvaus hae-toimenkuvan-kuukauden-johto-ja-hallintokorvaus
  hae-urakan-toimenkuvat hae-toimenkuvan-johto-ja-hallintokorvaukset-kuukausittain
  paivita-kuukauden-johto-ja-hallintokorvaus<!
  lisaa-kuukauden-johto-ja-hallintokorvaus<! hae-viimeisin-muokkaaja-jjh
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
        kiinteat (reduce (fn [acc {:keys [nimi koodi toimenpideinstanssi-id]}]
                           (let [kiinteat (hae-kiintea-kustannus-kuukausittain
                                            db {:sopimus-id sopimus-id
                                                :vuosi hoitovuoden-alkuvuosi
                                                :toimenpideinstanssi-id toimenpideinstanssi-id})
                                 kiinteat-alkukausi (filter #(>= (:kuukausi %) 10) kiinteat)
                                 kiinteat-loppukausi (filter #(<= (:kuukausi %) 9) kiinteat)
                                 alkukausi (if (seq kiinteat-alkukausi) (apply + (map (fn [rivi]
                                                                                        (if (:summa rivi)
                                                                                          (:summa rivi)
                                                                                          0))
                                                                                   kiinteat-alkukausi)) 0)
                                 alkukausi-indeksikorjattu (if (seq kiinteat-alkukausi)
                                                             (apply + (map (fn [rivi]
                                                                             (if (:summa_indeksikorjattu rivi)
                                                                               (:summa_indeksikorjattu rivi)
                                                                               0))
                                                                        kiinteat-alkukausi))
                                                             0)
                                 loppukausi (if (seq kiinteat-loppukausi) (apply + (map (fn [rivi]
                                                                                          (if (:summa rivi)
                                                                                            (:summa rivi)
                                                                                            0))
                                                                                     kiinteat-loppukausi)) 0)
                                 loppukausi-indeksikorjattu (if (seq kiinteat-loppukausi)
                                                              (apply + (map (fn [rivi]
                                                                              (if (:summa_indeksikorjattu rivi)
                                                                                (:summa_indeksikorjattu rivi)
                                                                                0))
                                                                         kiinteat-loppukausi))
                                                              0)]
                             (conj acc {:nimi nimi
                                        :koodi koodi
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
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :urakkaid urakka-id}))
        ;; Yhteenvetorivi
        yhteenveto {:nimi "Yhteensä"
                    :alkukausi (apply + (map :alkukausi kiinteat))
                    :alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu kiinteat))
                    :loppukausi (apply + (map :loppukausi kiinteat))
                    :loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu kiinteat))
                    :yhteensa (+ (apply + (map :alkukausi kiinteat)) (apply + (map :loppukausi kiinteat)))
                    :yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu kiinteat)) (apply + (map :loppukausi-indeksikorjattu kiinteat)))
                    :pysyvat-muutokset "Ei muutoksia"
                    :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                    :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}
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
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :tehtava-id (:id tehtava)
                                        :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        hoidonjohtopalkkiot (if (seq hoidonjohtopalkkiot)
                              ;; Jos on tallennettu jo hoidonjohtopalkkioita, niin lisätään niihin kalenterikuukausi
                              (map (fn [rivi]
                                     (merge rivi
                                       {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                             (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                        :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                        :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
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
                                         :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                         :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                         :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) hoidonjohtopalkkiot)))

(defn paattele-toimenkuvan-kuukaudet [urakan-alkuvuosi toimenkuva]
  (let [toimenkuva-nimi (:toimenkuva toimenkuva)
        toimenkuva-nimike (:nimike toimenkuva)]
   (cond
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "sopimusvastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Päätoiminen apulainen (talvikausi)")) [5 6 7 8 9]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Päätoiminen apulainen (kesäkausi)")) [10 11 12 1 2 3 4]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Apulainen/työnjohtaja (talvikausi)")) [5 6 7 8 9]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Apulainen/työnjohtaja (kesäkausi)")) [10 11 12 1 2 3 4]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [4 5 6 7 8]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "hankintavastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "harjoittelija")) [5 6 7 8]

     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "valmistelukausi ennen urakka-ajan alkua")) [8] ;; Tämä pitäisi olla ennen sopimuskautta. Mutta laitetaan sinne yksi kuukausi
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "päätoiminen apulainen")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "apulainen/työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "hankintavastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "harjoittelija")) [10 11 12 1 2 3 4 5 6 7 8 9]

     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "valmistelukausi ennen urakka-ajan alkua")) [8] ;; Tämäkin pitäisi olla ennen sopimuskautta. Mutta laitetaan sinne yksi kuukausi
     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "2. työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "3. työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [10 11 12 1 2 3 4 5 6 7 8 9]
     (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "harjoittelija")) [10 11 12 1 2 3 4 5 6 7 8 9]
     :else [10 11 12 1 2 3 4 5 6 7 8 9])))

(defn hae-johto-ja-hallintokorvaukset-2019-2024 [db urakka-id hoitovuoden-alkuvuosi urakan-alkuvuosi toimenkuvat-tarjouksesta]
  (let [viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-jjh
                                    db {:urakka-id urakka-id
                                        :vuosi hoitovuoden-alkuvuosi}))
        ;; Haetaan ensin urakkakohtaiset toimenkuvat
        toimenkuvat (hae-urakan-toimenkuvat db {:urakka-id urakka-id
                                                :urakan-alkuvuosi urakan-alkuvuosi})
        ;; 2019 - 2021 alkavien urakoiden toimenkuvat eivät löydy tietokantahaulla, koska ne on kovakoodattu fronttiin. Niille on kuitenkin annettu
        ;; joissain tapauksissa kaksi nimeä, mutta sama id. Joten joudumme taaksepäin yhteensopivuuden vuoksi tekemään muunnoksen
        toimenkuvat (if (<= urakan-alkuvuosi 2021)
                      (reduce (fn [uudet-toimenkuvat toimenkuva]
                                (let [uusi-toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "päätoiminen apulainen"
                                                         :nimike "Päätoiminen apulainen (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "apulainen/työnjohtaja"
                                                         :nimike "Apulainen/työnjohtaja (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        :else nil)

                                      toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Päätoiminen apulainen (kesäkausi)")
                                                   (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Apulainen/työnjohtaja (kesäkausi)")
                                                   :else (merge toimenkuva {:nimike (:toimenkuva toimenkuva)}))]

                                  ;; Lisätään talvi/kesäkausi vain, jos ne on noita erikois kovakoodattuja toimenkuvia
                                  (if uusi-toimenkuva
                                    (conj uudet-toimenkuvat uusi-toimenkuva toimenkuva)
                                    (conj uudet-toimenkuvat toimenkuva))))
                        [] toimenkuvat)
                      toimenkuvat)

        ;; Haetaan raskaalla prosessilla toimenkuvakohtaisesti suunnitellut johto-ja-hallintokorvaukset
        toimenkuvat (reduce (fn [kuvat toimenkuva]
                              (let [tarjous-rivi (first (filter #(= (:toimenkuva-id %) (:id toimenkuva)) toimenkuvat-tarjouksesta))
                                    tarjous-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot tarjous-rivi))))
                                    toimenkuva (assoc toimenkuva :tarjous-summa tarjous-summa)

                                    toimenkuvan-kuukaudet (paattele-toimenkuvan-kuukaudet urakan-alkuvuosi toimenkuva)

                                    kuukaudet (hae-toimenkuvan-johto-ja-hallintokorvaukset-kuukausittain
                                                db {:urakka-id urakka-id
                                                    :vuosi hoitovuoden-alkuvuosi
                                                    :toimenkuva-id (:id toimenkuva)
                                                    :sallitut-kuukaudet toimenkuvan-kuukaudet})

                                    ;; Jos kuukaudet on nil, niin luodaan lista default arvoilla.
                                    ;; Kuukausilistauksessa on kuitenkin valtavasti hajontaa sen perusteella, että mikä toimenkuva on kyseessä
                                    ;; Tässä on paljon historian painolastia ja kunhan vanhasta kustannusten suunnittelust apäästään kokonaan eroon,
                                    ;; niin toimenkuvat voidaan järkevöittää ja yhdenmukaistaa
                                    kuukaudet (if (seq kuukaudet)
                                                (map (fn [rivi]
                                                       (merge rivi
                                                         {:yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))
                                                          :yhteensa-indeksikorjattu-kk (* (if (:tuntipalkka-indeksikorjattu rivi) (:tuntipalkka-indeksikorjattu rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))
                                                          :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                                               (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                                          :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                          :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                                                          :nimike (:nimike toimenkuva)}))
                                                  kuukaudet)
                                                (mapv (fn [kk]
                                                        (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                                          {:id (:id toimenkuva)
                                                           :toimenkuva (:toimenkuva toimenkuva)
                                                           :nimike (:nimike toimenkuva)
                                                           :urakka-id urakka-id
                                                           :kuukausi kk
                                                           :yhteensa-kk 0
                                                           :vuosi vuosi
                                                           :tuntipalkka 0
                                                           :tuntipalkka-indeksikorjattu nil
                                                           :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                                           :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                           :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                                  toimenkuvan-kuukaudet))

                                    ;; Vanhoilla toimenkuvilla voi tulla vain osittaiset kuukaudet, esim talvikausi, joka on syyskuusta maaliskuuhun
                                    ;; Täytetään kuukausilista näissä tapauksissa default arvoilla
                                    kuukaudet (reduce (fn [uudet-kuukaudet kk]
                                                        (let [valittu-kuukausi (first (filter #(= (:kuukausi %) kk) kuukaudet))]
                                                          (if valittu-kuukausi
                                                            (conj uudet-kuukaudet valittu-kuukausi)
                                                            (conj uudet-kuukaudet
                                                              {:id (:id toimenkuva)
                                                               :toimenkuva (:toimenkuva toimenkuva)
                                                               :nimike (:nimike toimenkuva)
                                                               :urakka-id urakka-id
                                                               :kuukausi kk
                                                               :vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))
                                                               :yhteensa-kk 0
                                                               :tuntipalkka 0
                                                               :tuntipalkka-indeksikorjattu nil
                                                               :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))) true)
                                                               :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                               :viimeisin-muokkaaja (:viimeisin-muokkaaja viimeisin-muokkaus)}))))
                                                [] toimenkuvan-kuukaudet)
                                    toimenkuva (assoc toimenkuva :kuukaudet kuukaudet
                                                 :kkv (count toimenkuvan-kuukaudet))
                                    summa (apply + (map
                                                     (fn [rivi] (if (and (:tuntipalkka rivi) (:tunnit rivi))
                                                                  (* (:tuntipalkka rivi) (:tunnit rivi)) 0))
                                                     kuukaudet))
                                    summa-indeksikorjattu (apply + (map
                                                                     (fn [rivi] (if (and (:tuntipalkka-indeksikorjattu rivi) (:tunnit rivi))
                                                                                  (* (:tuntipalkka-indeksikorjattu rivi) (:tunnit rivi)) 0))
                                                                     kuukaudet))
                                    toimenkuva (assoc toimenkuva
                                                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                                                 :tuntipalkka (:tuntipalkka (first kuukaudet))
                                                 :tunnit (if (kust-domain/onko-tunnit-samat? kuukaudet) (:tunnit (first kuukaudet))
                                                           nil) ;; Aseta arvo buk, jos tunnit eivät ole samat kaikissa kuukausissa
                                                 :yhteensa-kk (* (or (:tuntipalkka (first kuukaudet)) 0) (or (:tunnit (first kuukaudet)) 0))
                                                 :summa summa
                                                 :summa-indeksikorjattu summa-indeksikorjattu)]
                                (conj kuvat toimenkuva)))
                      [] toimenkuvat)

        ;; Lisää vielä järjestysnumero toimenkuville
        toimenkuvat (map-indexed (fn [i toimenkuva]
                                  (assoc toimenkuva :jarjestys (inc i)))
                                toimenkuvat)]
    toimenkuvat))

(defn hae-johto-ja-hallintokorvaukset [db urakka-id hoitovuoden-alkuvuosi toimenkuvat-tarjouksesta]
  (let [johto-ja-hallintokorvaukset (hae-johto-ja-hallintokorvaukset-kuukausittain db
                                      {:urakka-id urakka-id
                                       :vuosi hoitovuoden-alkuvuosi})
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-jjh
                                    db {:urakka-id urakka-id
                                        :vuosi hoitovuoden-alkuvuosi}))

        johto-ja-hallintokorvaukset (if (seq johto-ja-hallintokorvaukset)
                                      ;; Jos on tallennettu jo johto-ja-hallintokorvauksia, niin lisätään niihin kalenterikuukausi
                                      (map (fn [rivi]
                                             (merge rivi
                                               {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                                     (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                                :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                        johto-ja-hallintokorvaukset)
                                      ;; Jos ei ole tallennettu hoidonjohtopalkkioita, niin luodaan nolla arvot
                                      (mapv (fn [kk]
                                              (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                                {:id nil
                                                 :urakka-id urakka-id
                                                 :kuukausi kk
                                                 :vuosi vuosi
                                                 :summa 0
                                                 :summa_indeksikorjattu nil
                                                 :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                        [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) johto-ja-hallintokorvaukset)))

(defn hae-erillishankinnat [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))

        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-erillishankinnoille
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :tehtavaryhma-id (:id tehtavaryhma)
                                        :toimenpideinstanssi-id hoidonjohto-tpi-id}))

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
                                                          (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                     :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                     :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
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
                                      :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                      :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                      :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
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

(defn tallenna-hankintojen-kuukausittainen-summa [db kk-jakso alkujakso? viimeinen-summa summa hoitovuoden-alkuvuosi sopimus-id
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
                  _ (if (:id dbrivi)
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
        _ (doseq [{:keys [alkukausi loppukausi toimenpideinstanssi-id]} kilpailutettavat-hankinnat]
            (let [alkukausi (bigdec alkukausi)

                  alkukausi-kuukausisumma (yleiset/round2 2 (with-precision 4 (/ alkukausi 3)))
                  alkukausi-viimeinen-kuukausi (- alkukausi (* 2 alkukausi-kuukausisumma))
                  loppukausi (bigdec loppukausi)
                  loppukausi-kuukausisumma (yleiset/round2 2 (with-precision 4 (/ loppukausi 9)))
                  loppukausi-viimeinen-kuukausi (- loppukausi (* 8 loppukausi-kuukausisumma))
                  ;; Tallenna alkujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 10 13) true alkukausi-viimeinen-kuukausi alkukausi-kuukausisumma
                      hoitovuoden-alkuvuosi sopimus-id toimenpideinstanssi-id (:id kayttaja))
                  ;; Tallenna loppujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 1 10) false loppukausi-viimeinen-kuukausi loppukausi-kuukausisumma
                      (inc hoitovuoden-alkuvuosi) sopimus-id toimenpideinstanssi-id (:id kayttaja))]))]))

(defn tallenna-erillishankinnat
  [db kayttaja urakka-id erillishankinnat]
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
                  _ (if (:id dbrivi)
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

(defn tallenna-kuukausittaiset-toimenkuvat [db kuukaudet urakan-indeksit urakan-tiedot kayttaja urakka-id toimenkuva-id]
  (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))]
    (doseq [{:keys [vuosi kuukausi tunnit tuntipalkka] :as rivi} (sort-by (juxt :vuosi :kuukausi) kuukaudet)]
      (let [;; Haetaan toimenkuvan kuukauden johto-ja-hallintokorvaus
            db-kuukausi (first (hae-toimenkuvan-kuukauden-johto-ja-hallintokorvaus db {:urakka-id urakka-id
                                                                                       :toimenkuva-id toimenkuva-id
                                                                                       :vuosi vuosi
                                                                                       :kuukausi kuukausi}))
            tunnit (if (>= urakan-alkuvuosi 2022)
                     1 ;; Kaikissa -22 tai myöhemmin alkaneissa urakoissa käytetään kokonaishintaa. Yksittäistä tuntia ei enää tallenneta
                     tunnit)

            t (if-not db-kuukausi
                (lisaa-kuukauden-johto-ja-hallintokorvaus<! db
                  {:urakka-id urakka-id
                   :toimenkuva-id toimenkuva-id
                   :vuosi vuosi
                   :kuukausi kuukausi
                   :tunnit tunnit
                   :tuntipalkka tuntipalkka
                   :tuntipalkka_indeksikorjattu (when tuntipalkka
                                                  (indeksi-kyselyt/indeksikorjaa
                                                    (indeksi-kyselyt/indeksikerroin urakan-indeksit
                                                      (pvm/paivamaara->mhu-hoitovuosi-nro
                                                        (:alkupvm urakan-tiedot) (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                                    tuntipalkka))
                   :luoja (:id kayttaja)})
                (paivita-kuukauden-johto-ja-hallintokorvaus<! db
                  {:id (:id db-kuukausi)
                   :tuntipalkka tuntipalkka
                   :tunnit tunnit
                   :tuntipalkka_indeksikorjattu (when tuntipalkka
                                                  (indeksi-kyselyt/indeksikorjaa
                                                    (indeksi-kyselyt/indeksikerroin urakan-indeksit
                                                      (pvm/paivamaara->mhu-hoitovuosi-nro
                                                        (:alkupvm urakan-tiedot) (pvm/luo-pvm-dec-kk vuosi kuukausi 1)))
                                                    tuntipalkka))
                   :muokkaaja (:id kayttaja)}))]))))

(defn tallenna-vuosittaiset-toimenkuvat [db rivi urakan-indeksit urakan-tiedot kayttaja urakka-id toimenkuva-id]
  (let [dbrivi (first (hae-kuukauden-johto-ja-hallintokorvaus db {:id (:id rivi)}))
        _ (if (:id dbrivi)

            (paivita-kuukauden-johto-ja-hallintokorvaus<! db
              {:id (:id dbrivi)
               :tuntipalkka (:summa rivi)
               :tunnit 1
               :tuntipalkka_indeksikorjattu (when (:summa rivi)
                                              (indeksi-kyselyt/indeksikorjaa
                                                (indeksi-kyselyt/indeksikerroin urakan-indeksit
                                                  (pvm/paivamaara->mhu-hoitovuosi-nro
                                                    (:alkupvm urakan-tiedot) (pvm/luo-pvm-dec-kk (:vuosi rivi) (:kuukausi rivi) 1)))
                                                (:summa rivi)))
               :muokkaaja (:id kayttaja)})
            ;; Lisää uusi
            (lisaa-kuukauden-johto-ja-hallintokorvaus<! db
              {:urakka-id urakka-id
               :toimenkuva-id toimenkuva-id
               :vuosi (:vuosi rivi)
               :kuukausi (:kuukausi rivi)
               :tunnit 1
               :tuntipalkka (:summa rivi)
               :tuntipalkka_indeksikorjattu nil
               :luoja (:id kayttaja)}))]))

(defn tallenna-johto-ja-hallintokorvaukset
  [db kayttaja urakka-id johto-ja-hallintokorvaukset]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)

        toimenpideinstanssi-id (:id (first
                                      (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                        {:urakka urakka-id
                                         :koodi (mhu/toimenpide-avain->toimenpide :mhu-johto)})))

        ; Tallenna kuukausittaiset summat
        _ (doseq [toimenkuva johto-ja-hallintokorvaukset]
            (let [;; rivi voi sisältää joko kuukausisumman kaikille toimenkuville (silloin id 1) tai
                  ;; toimenkuvan, jolla on kuukausittaiset arvot, tunnit ja tuntipalkat.
                  kuukaudet (when (<= urakan-alkuvuosi 2024)
                              (:kuukaudet toimenkuva))

                  ;; Toimenkuva on tietokannassa pakollinen.
                  ;; Asetetaan jokin toimenkuva myös 2025-> urakoille, koska oikeaa toimenkuvaa ei voida uudessa kustannusten suunnittelussa asettaa.
                  ;; Kuukausittaiset yhteenvetorivit eivät ole riippuvaisia toimenkuvasta, joten voidaan käyttää mitä tahansa.
                  toimenkuva-id (if (>= urakan-alkuvuosi 2025)
                                  1
                                  (:id toimenkuva))

                  _ (if (<= urakan-alkuvuosi 2024)
                      (tallenna-kuukausittaiset-toimenkuvat db kuukaudet urakan-indeksit urakan-tiedot kayttaja urakka-id toimenkuva-id)
                      (tallenna-vuosittaiset-toimenkuvat db toimenkuva urakan-indeksit urakan-tiedot kayttaja urakka-id toimenkuva-id))]))
        _ (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
        _ (kiint-kyselyt/merkitse-maksuerat-likaisiksi-hoidonjohdossa! db {:toimenpideinstanssi toimenpideinstanssi-id})]))

(defn tallenna-hoidonjohtopalkkiot
  [db kayttaja urakka-id hoidonjohtopalkkiot]
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
                  _ (if (:id dbrivi)
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

(defn voidaanko-vahvistaa-tavoitehintaa?
  "Jotta urakan tavoitehinta voidaan vahvistaa, Kustannusten Suunnittelussa pitää olla täydennettynä kaikki tiedot."
  [db urakka-id hoitovuoden-alkuvuosi]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)

        ;; Kaikki kustannussuunnitelman summat vaikuttaa tavoitehintaan
        ;; Pysyvät muutokset lisätään mukaan joko vähentämään tai lisäämään tavoitehintaa
        kilpailutettavat-hankinnat (hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        hankinnat-ok? (and (boolean (seq kilpailutettavat-hankinnat))
                        (some (fn [x] (not= (:yhteensa x) 0)) kilpailutettavat-hankinnat))

        erillishankinnat (hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        erillishankinnat-ok? (and (boolean (seq erillishankinnat))
                               (some (fn [x] (not= (:summa x) 0)) erillishankinnat))

        hoidonjohtopalkkiot (hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        hoidonjohtopalkkiot-ok? (and (boolean (seq hoidonjohtopalkkiot))
                                  (some (fn [x] (not= (:summa x) 0)) hoidonjohtopalkkiot))

        johto-ja-hallintokorvaukset (hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        johto-ja-hallintokorvaukset-ok? (and (boolean (seq johto-ja-hallintokorvaukset))
                                          (some (fn [x] (not= (:summa x) 0)) hoidonjohtopalkkiot))
        voidaan-vahvistaa? (every? true? [hankinnat-ok? erillishankinnat-ok? hoidonjohtopalkkiot-ok?
                                          johto-ja-hallintokorvaukset-ok?])]
    voidaan-vahvistaa?))

(defn vahvista-tavoite-ja-kattohinta [db kayttaja urakka-id vahvista? hoitovuoden-alkuvuosi]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitokausinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        vahvistus-pvm (pvm/nyt)
        hoitokauden-alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
        hoitokauden-loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))
        _ (vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})

        ;; Rahavaraukset on näytetty tähän asti tarjouksen tiedoista. Kopioidaan ne nyt kustannusarvoitu_tyo tauluun
        ;; Hae ensin tarjouksen tiedot
        tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)
        rahavaraukset (filter #(= "tavoitehintaiset-rahavaraukset" (:osio %)) (:tarjous tarjous))

        _ (mapv (fn [rahavaraus]
                  (let [rahavaraus-id (:rahavaraus-id rahavaraus)
                        vuosittainen-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot rahavaraus))))

                        ;; Jokaisella kustannusarvoitu_tyo -rivillä pitää olla toimenpideinstanssi.
                        ;; Rahavaraukset eivät kuulu millekään tällä hetkellä tiedetylle toimenpideinstanssille.
                        ;; Mutta yksinkertaisuuden vuoksi toimenpideinstanssin pakollisuutta ei lähdetty muuttamaan, vaan laitetaan
                        ;; Rahavaraukselle vain jokin toimenpideinstanssi. Sen olemassaolo filtteröidään muualla pois.
                        ensimmainen-toimenpideinstanssi-id (:id (first (rahavaraus-kyselyt/hae-rahavarauksen-toimenpideinstanssi db {:urakka_id urakka-id})))

                        ;; Päivitetään rahavarauksen summa ja indeksikorjattu summa kustannusarvioitu_työ tauluun
                        kt-rahavaraus-kuukaudet (ka-q/hae-rahavarauskustannus db {:rahavaraus_id rahavaraus-id
                                                                                  :vuosi hoitovuoden-alkuvuosi
                                                                                  :sopimus_id sopimus-id})

                        dbrahavaraus (if (seq kt-rahavaraus-kuukaudet)
                                       (let [kk (atom 0)] ;; Lokaalisti voi olla vaikka vain kolmena kuukautena summa, vaikka pitäisi olla 12
                                         (doseq [r kt-rahavaraus-kuukaudet
                                                 :let [_ (swap! kk inc)
                                                       kuukausimaara (count kt-rahavaraus-kuukaudet)
                                                       kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa kuukausimaara))) ;; Tallenna nil kantaan, jos nil arvo on syötetty
                                                       viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* (dec kuukausimaara) kuukausisumma))))]]
                                           ;; Rahavarauksesta ei voi muuttua, kuin summa
                                           (ka-q/paivita-rahavaraus<! db {:summa (if (and (>= kuukausimaara 9) (= @kk 9)) viimeinen-kuukausisumma kuukausisumma)
                                                                          :muokattu (pvm/nyt)
                                                                          :muokkaaja (:id kayttaja)
                                                                          :id (:id r)})))
                                       (doseq [kk (range 1 13)
                                               :let [kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa 12)))
                                                     viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* 11 kuukausisumma))))]]
                                         (ka-q/lisaa-rahavaraus<! db {:vuosi (if (< kk 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
                                                                      :kuukausi kk
                                                                      :sopimus_id sopimus-id
                                                                      :toimenpideinstanssi_id ensimmainen-toimenpideinstanssi-id
                                                                      :tehtava_id nil
                                                                      :rahavaraus_id rahavaraus-id
                                                                      :summa (if (= kk 9) viimeinen-kuukausisumma kuukausisumma)
                                                                      :luoja (:id kayttaja)})))]
                    dbrahavaraus))
            rahavaraukset)

        _ (vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})
        _ (vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})
        _ (vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille! db
            {:urakka-id urakka-id
             :vuosi hoitovuoden-alkuvuosi
             :hoitovuosi-nro hoitokausinumero
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})]))
