(ns harja.palvelin.palvelut.valikatselmus.paatokset-ketjutus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.testi :refer :all]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valikatselmus.apurit :as v-apurit]
            [harja.palvelin.palvelut.valikatselmus.paatostyypit :as paatostyypit]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :db-replica (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :valikatselmus (component/using
                           (valikatselmukset/->Valikatselmukset)
                           [:http-palvelin :db :db-replica])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
  (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(defn- paatosavaimet [paatokset]
  (set (map :avain paatokset)))


(defn valitse-paatos
  [paatokset avain]
  (get (first (filter #(= (ffirst %) avain) paatokset)) avain))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-koko-ketju-toimii
  (let [vastaus (v-apurit/hae-ketjutetusti-kumoutuvat-paatokset
                  paatostyypit/paatostyypit
                  :tavoitehinnan-muutokset)]

    (is (= #{:indeksikorjaus
             :hoitovuoden-lopun-hinta
             :tavoitehinnan-alitus
             :tavoitehinnan-ylitys
             :kattohinnan-ylitys
             :lupaus
             :hoidonjohtopalkkio}
          (paatosavaimet vastaus)))

    (is (not (contains? (paatosavaimet vastaus) :raportti)))))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-raportti-toimii
  (is (empty?
        (v-apurit/hae-ketjutetusti-kumoutuvat-paatokset
          paatostyypit/paatostyypit
          :raportti))))


(deftest hae-ketjutetusti-kumoutuvat-paatokset-palvelu-palauttaa-vain-tehdyt
  (let [hoitokauden-alkuvuosi 2024
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")

        tehdyt [{:id 10
                 :nimi "Hoitovuoden lopun indeksikorjaus"}
                {:id 20
                 :nimi "Tavoitehinnan ylitys"}]

        peruttava {:urakkaid urakkaid
                   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                   :nimi "Tavoitehinnan muutokset"
                   :avain :tavoitehinnan-muutokset}

        vastaus (with-redefs [valikatselmukset/hae-urakan-mahdolliset-paatokset
                              (fn [_db _kayttaja _payload]
                                paatostyypit/paatostyypit)
                              valikatselmukset/palauta-kaikki-mahdolliset-ja-tehdyt-paatokset
                              (fn [_db _kayttaja _payload]
                                {:tietokanta-paatokset tehdyt})]
                  (valikatselmukset/hae-ketjutetusti-kumoutuvat-paatokset
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    peruttava))]

    (is (= #{10 20}
          (set (map :id vastaus))) "Vastaus täsmää tehtyjä päätöksiä")

    (is (= #{:indeksikorjaus
             :tavoitehinnan-ylitys}
          (paatosavaimet vastaus)))))


(deftest poista-paatokset-ketjutetusti-funktiohierarkia-toimii
  (let [urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        hoitokauden-alkuvuosi 2024

        paatos {:id 1
                :urakkaid urakkaid
                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                :avain :tavoitehinnan-muutokset}

        kumoutuvat [{:id 2
                     :avain :indeksikorjaus}
                    {:id 3
                     :avain :hoitovuoden-lopun-hinta}
                    {:id 4
                     :avain :tavoitehinnan-ylitys}]

        poistot (atom [])
        odotettu-vastaus {:paatokset :haettu}]

    (with-redefs [valikatselmukset/poista-yksittainen-paatos
                  (fn [_db _kayttaja poistettava]
                    (swap! poistot conj (:id poistettava)))

                  valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle
                  (fn [_db _kayttaja payload]
                    (is (= {:urakkaid urakkaid
                            :hoitovuosi hoitokauden-alkuvuosi}
                          payload))
                    odotettu-vastaus)]

      (is (= odotettu-vastaus
            (valikatselmukset/poista-paatokset-ketjutetusti
              (:db jarjestelma)
              +kayttaja-jvh+
              {:paatos paatos
               :tehdyt-kumoutuvat-paatokset kumoutuvat}))))

    (is (= [1 2 3 4] @poistot))))


(deftest paatosten-poisto-ketjutetusti-toimii

  (let [urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)

        ;; -----------------------------
        ;; Tavoitehinnan muutospäätös
        kattohinta 5M
        tavoitehinta 5M
        hoitokauden-alkuvuosi 2025
        kayttajaid (:id +kayttaja-jvh+)

        paatos (paatos-apurit/tavoitehinnan-muutospaatos
                 urakkaid
                 hoitokauden-alkuvuosi
                 muokkaa-kattohinta
                 tavoitehinta
                 kattohinta
                 kayttajaid)

        vastaus-tavoitahinnan-muutos (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) paatos kayttajaid)
        ;; Katso että päätös tallentui
        ;; Kaikkia arvoja ei tässä testissä tarvitse tarkistella
        _ (is (= urakkaid (:urakkaid vastaus-tavoitahinnan-muutos)))
        _ (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus-tavoitahinnan-muutos)))
        _ (is (= muokkaa-kattohinta (:muokkaa_kattohinta vastaus-tavoitahinnan-muutos)))


        ;; -----------------------------
        ;; Lopun päätös 
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        tavoitehinnan_muutokset 40000M
        hoitokauden-lopun-indeksikorjaus 40000M

        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)

        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)

        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        vastaus-hintapaatos (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) paatos)
        ;; Katso että päätös tallentui
        ;; Kaikkia arvoja ei tässä testissä tarvitse tarkistella
        _ (is (= urakkaid (:urakkaid vastaus-hintapaatos)))
        _ (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus-hintapaatos)))
        _ (is (= tavoitehinta_ennen (:tavoitehinta_ennen vastaus-hintapaatos)))


        ;; -----------------------------
        ;; Hoidonjohtopalkkion päätös 
        kulu_id 1
        hoidonjohtopalkkio 40000M
        tarjouksen_tavoitehinta 2000000M
        hv_lopun_indkorjaamaton_tavoitehinta 2100000M

        muutosprosentti (* (- (/ hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta) 1) 100)
        hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)

        paatos (paatos-apurit/hoidojohtopalkkiomuutospaatos
                 urakkaid
                 hoitokauden-alkuvuosi
                 hv_lopun_indkorjaamaton_tavoitehinta
                 tarjouksen_tavoitehinta
                 muutosprosentti
                 hoidonjohtopalkkio
                 hoidonjohtopalkkio_muutos
                 kulu_id
                 kayttajaid)

        vastaus-hoidonjohtopalkkio-muutos (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) paatos)
        ;; Katso että päätös tallentui
        ;; Kaikkia arvoja ei tässä testissä tarvitse tarkistella
        _ (is (= urakkaid (:urakkaid vastaus-hoidonjohtopalkkio-muutos)))
        _ (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus-hoidonjohtopalkkio-muutos)))
        _ (is (= hv_lopun_indkorjaamaton_tavoitehinta (:hv_lopun_indkorjaamaton_tavoitehinta vastaus-hoidonjohtopalkkio-muutos)))]


    (testing "Ketjutus haku, sekä kumoaminen toimii"
      (let [valikatselmus-vastaus (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle
                                    (:db jarjestelma) +kayttaja-jvh+
                                    {:urakkaid urakkaid :hoitovuosi hoitokauden-alkuvuosi})

            _ (is (some? (:id (valitse-paatos (:paatokset valikatselmus-vastaus) :hoitovuoden-lopun-tavoite-ja-kattohinta)))
                "Hoitovuoden lopun tavoite- ja kattohintapäätös on olemassa")
            _ (is (some? (:id (valitse-paatos (:paatokset valikatselmus-vastaus) :hoidonjohtopalkkion-muutos)))
                "Hoidonjohtopalkkion muutospäätös on olemassa")

            peruttava-paatos (valitse-paatos (:paatokset valikatselmus-vastaus) :hoitovuoden-lopun-tavoite-ja-kattohinta)
            _ (is (not (nil? (:urakkaid peruttava-paatos))))


            ;; -----------------------------
            ;; Hae kaikki kumoutuvat
            tehdyt-kumoutuvat-paatokset (kutsu-palvelua
                                          (:http-palvelin jarjestelma)
                                          :hae-ketjutetusti-kumoutuvat-paatokset +kayttaja-jvh+
                                          peruttava-paatos)

            peruuntuva-paatostyyppi (some->> tehdyt-kumoutuvat-paatokset first :paatostyyppi)

            _ (is (= peruuntuva-paatostyyppi "hoidonjohtopalkkio")
                "Hoidonjohtopalkkio peruuntuu lopun tavoite-ja-kattohinta päätöksen mukana")


            poista-paatokset-vastaus (kutsu-palvelua
                                       (:http-palvelin jarjestelma)
                                       :poista-paatokset-ketjutetusti +kayttaja-jvh+
                                       {:urakka-id urakkaid
                                        :paatos (assoc peruttava-paatos :luoja kayttajaid)
                                        :tehdyt-kumoutuvat-paatokset tehdyt-kumoutuvat-paatokset})

            _ (is (nil? (:id (valitse-paatos (:paatokset poista-paatokset-vastaus) :hoitovuoden-lopun-tavoite-ja-kattohinta)))
                "Hoitovuoden lopun tavoite- ja kattohintapäätös poistettiin")
            _ (is (nil? (:id (valitse-paatos (:paatokset poista-paatokset-vastaus) :hoidonjohtopalkkion-muutos)))
                "Hoidonjohtopalkkion muutospäätös poistettiin")]))))
