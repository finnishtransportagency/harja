(ns harja.palvelin.palvelut.tiemerkintojen-kustannus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.kustannusten-kirjaus :as tiemerkkarit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tiemerkinta-korjauskustannus (component/using
                                          (tiemerkkarit/->TiemerkinnanKustannusKirjaukset)
                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(defn- tee-kutsu [params kutsu]
  (kutsu-palvelua (:http-palvelin jarjestelma) kutsu +kayttaja-jvh+ params))

(deftest tallennus-paivitys-toimii
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))
        kustannusvuosi 2020
        kustannus-tallennus 123456
        kustannus-paivitys 654321
        params (conj [] {:urakka urakka-id
                         :muokkaaja 3
                         :kustannusvuosi kustannusvuosi
                         :kustannus kustannus-tallennus
                         :pk1 25.0M
                         :pk2 15.0M
                         :pk3 60.0M})
        haku-ennen (kutsu-palvelua
                     (:http-palvelin jarjestelma)
                     :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                     {:urakka urakka})
        ;;Konversion namespace
        _ (println "hake-ennen " haku-ennen)
        kustannus-ennen (filter #(= (:kustannusvuosi %) kustannusvuosi) haku-ennen)

        ;;lisää uuden kustannuksen
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot params})

        tallennuksen-jalkeen (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                               {:urakka urakka})
        kustannust (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) tallennuksen-jalkeen))
        _ (is (= (:urakka kustannust) urakka-id))
        _ (is (= (int (:kustannus kustannust)) kustannus-tallennus))

        ;;päivittää aiemmin lisättyä kustannusta
        paivita-params (conj [] (assoc (first params) :kustannus kustannus-paivitys))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot paivita-params})
        paivityksen-jalkeen (kutsu-palvelua
                              (:http-palvelin jarjestelma)
                              :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                              {:urakka urakka})
        kustannusp (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) paivityksen-jalkeen))
        _ (is (= (:urakka kustannusp) urakka-id))
        _ (is (= (int (:kustannus kustannusp)) kustannus-paivitys))]
    (u (str "DELETE FROM tiemerkinta_korjauskustannus WHERE urakka = 12 AND kustannusvuosi = " kustannusvuosi))))
