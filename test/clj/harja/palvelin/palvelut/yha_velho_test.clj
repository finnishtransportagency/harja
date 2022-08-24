(ns harja.palvelin.palvelut.yha-velho-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.integraatiot.yha.urakan-kohdehaku-test :as urakan-kohdehaku-test]
            [harja.palvelin.integraatiot.yha.urakoiden-haku-test :as urakoiden-haku-test]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [harja.jms-test :refer [feikki-jms]]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<!! timeout]]
            [harja.palvelin.palvelut.yha-velho :as yha-velho]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha-integraatio]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.kyselyt.yha :as yha-kyselyt]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.konversio :as konv])
  (:use org.httpkit.fake))

(def +yha-url+ "http://localhost:1234/")
(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil) [:db])
                        :yha-velho (component/using
                               (yha-velho/->YhaVelho)
                               [:http-palvelin :db :yha-integraatio :velho-integraatio])
                        :yha (component/using
                               (yha/->Yha)
                               [:http-palvelin :db :yha-integraatio])
                        :yha-integraatio (component/using
                                           (yha-integraatio/->Yha {:url +yha-url+})
                                           [:db :integraatioloki])
                        :velho-integraatio (component/using
                                           (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                                       :token-url +velho-token-url+
                                                                       :kayttajatunnus "abc-123"
                                                                       :salasana "blabla"})
                                           [:db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

#_(deftest laheta-pot-yhaan-ja-velhoon                      ; TODO enable VELHO
  (let [[kohde-id pot2-id urakka-id] (hae-pot2-testi-idt)
        _ (asenna-pot-lahetyksen-tila kohde-id pot2-id)
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :laheta-pot-yhaan-ja-velhoon
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :kohde-id kohde-id})]
    (is (false? (:lahetys-onnistunut tulos)))
    (is (= "tekninen-virhe" (:velho-lahetyksen-tila tulos)))
    (is (re-matches #".*Ulkoiseen järjestelmään ei saada yhteyttä.*" (:lahetysvirhe tulos)))
    (is (re-matches #".*Ulkoiseen järjestelmään ei saada yhteyttä.*" (:velho-lahetyksen-vastaus tulos)))))

