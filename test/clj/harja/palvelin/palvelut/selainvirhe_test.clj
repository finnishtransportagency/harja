(ns harja.palvelin.palvelut.selainvirhe-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.selainvirhe :refer :all]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))


(deftest raportoi-selainvirhe-testi
  (let [kayttaja +kayttaja-jvh+
        viesti "Uncaught TypeError: Failed to construct 'ErrorEvent': 1 argument required, but only 0 present."
        url "http://localhost:3000/js/out/harja/views/hallinta/yhteydenpito.js"
        sijainti "http://localhost:3000/#hallinta/yhteydenpito?"
        rivi "11"
        sarake "8"
        selain "Mozilla/5.0 (Macintosh); Intel Mac OS X 10_12_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36"
        stack "TypeError: Failed to construct 'ErrorEvent': 1 argument required, but only 0 present.
                at http://localhost:3000/js/out/harja/views/hallinta/yhteydenpito.js:11:8
                at Object.ReactErrorUtils.invokeGuardedCallback (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:9017:16)
                at executeDispatch (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:3006:21)
                at Object.executeDispatchesInOrder (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:3029:5)
                at executeDispatchesAndRelease (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:2431:22)
                at executeDispatchesAndReleaseTopLevel (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:2442:10)
                at Array.forEach (native)
                at forEachAccumulated (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:15423:9)
                at Object.processEventQueue (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:2645:7)
                at runEventQueueInBatch (http://localhost:3000/js/out/cljsjs/react-dom/development/react-dom.inc.js:9041:18)))"
        selainvirhe {:url url
                     :sijainti sijainti
                     :viesti viesti
                     :rivi   rivi
                     :sarake sarake
                     :selain selain
                     :stack stack}
        formatoitu-virhe (formatoi-selainvirhe kayttaja selainvirhe)
        formatoitu-virhe-ilman-stack (formatoi-selainvirhe kayttaja (assoc selainvirhe :stack nil))]
    (is (= formatoitu-virhe {:fields  [{:title "Selainvirhe" :value viesti}
                                       {:title "Sijainti Harjassa" :value sijainti}
                                       {:title "URL" :value url}
                                       {:title "Selain" :value selain}
                                       {:title "Rivi" :value rivi}
                                       {:title "Sarake" :value sarake}
                                       {:title "Käyttäjä" :value (str (:kayttajanimi kayttaja) " (" (:id kayttaja) ")")}
                                       {:title "Stack" :value stack}]}))
    (is (= formatoitu-virhe-ilman-stack {:fields  [{:title "Selainvirhe" :value viesti}
                                                   {:title "Sijainti Harjassa" :value sijainti}
                                                   {:title "URL" :value url}
                                                   {:title "Selain" :value selain}
                                                   {:title "Rivi" :value rivi}
                                                   {:title "Sarake" :value sarake}
                                                   {:title "Käyttäjä" :value (str (:kayttajanimi kayttaja) " (" (:id kayttaja) ")")}]}))))
(deftest raportoi-yhteyskatkos-testi
  (let [kayttaja +kayttaja-jvh+
        ping-1 {:aika (pvm/luo-pvm 2000 1 1) :palvelu :ping}
        ping-2 {:aika (pvm/nyt) :palvelu :ping}
        hae-ilmoitukset {:aika (pvm/nyt) :palvelu :hae-ilmoitukset}
        yhteyskatkos {:yhteyskatkokset [ping-1 hae-ilmoitukset ping-2]}
        formatoitu-yhteyskatkos (formatoi-yhteyskatkos kayttaja yhteyskatkos)]
    (is (= formatoitu-yhteyskatkos {:text (str "Käyttäjä " (:kayttajanimi kayttaja) " (" (:id kayttaja) ")" " raportoi yhteyskatkoksista palveluissa:")
                                    :fields [{:title ":ping" :value (str "Katkoksia 2 kpl(slack-n)ensimmäinen: " (c/from-date (:aika ping-1))
                                                                         "(slack-n)viimeinen: " (c/from-date (:aika ping-2)))}
                                             {:title ":hae-ilmoitukset" :value (str "Katkoksia 1 kpl(slack-n)ensimmäinen: " (c/from-date (:aika hae-ilmoitukset))
                                                                                    "(slack-n)viimeinen: " (c/from-date (:aika hae-ilmoitukset)))}]}))))
