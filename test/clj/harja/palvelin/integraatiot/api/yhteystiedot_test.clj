(ns harja.palvelin.integraatiot.api.yhteystiedot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.yhteystiedot :as api-yhteystiedot]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.komponentit.fim :as fim]))

(def livi-jarjestelmakayttaja "livi")
(def urakoitsija-jarjestelmakayttaja "skanska")

(def fim-url "https://localhost:6666/FIM/SimpleREST4FIM/1/Group.svc/getGroupUsersFromEntitity")

(def fim-vastaus "<Members><member>
<AccountName>A000014</AccountName>
<FirstName>Tarja</FirstName>
<LastName>Bäck</LastName>
<DisplayName>Bäck Tarja</DisplayName>
<Email>tarja.back@ely-keskus.fi</Email>
<MobilePhone></MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>A009910</AccountName>
<FirstName>Jorma</FirstName>
<LastName>Lusikka</LastName>
<DisplayName>Lusikka Jorma</DisplayName>
<Email>jorma.lusikka@ely-keskus.fi</Email>
<MobilePhone>+358400290283</MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>A010008</AccountName>
<FirstName>Irma</FirstName>
<LastName>Petäjäjärvi</LastName>
<DisplayName>Petäjäjärvi Irma</DisplayName>
<Email>irma.petajajarvi@ely-keskus.fi</Email>
<MobilePhone>+358407265193</MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>A010150</AccountName>
<FirstName>Pekka</FirstName>
<LastName>Toiviainen</LastName>
<DisplayName>Toiviainen Pekka</DisplayName>
<Email>pekka.toiviainen@ely-keskus.fi</Email>
<MobilePhone>+358400293138</MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>A004133</AccountName>
<FirstName>Jukka</FirstName>
<LastName>Vanhanen</LastName>
<DisplayName>Vanhanen Jukka</DisplayName>
<Email>jukka.vanhanen@ely-keskus.fi</Email>
<MobilePhone></MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>A010186</AccountName>
<FirstName>Elisa</FirstName>
<LastName>Virta</LastName>
<DisplayName>Virta Elisa</DisplayName>
<Email>elisa.virta@ely-keskus.fi</Email>
<MobilePhone></MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>U007790</AccountName>
<FirstName>Tero</FirstName>
<LastName>Vilen</LastName>
<DisplayName>Vilen Tero</DisplayName>
<Email>tero.vilen@carement.fi</Email>
<MobilePhone>+358401848982</MobilePhone>
<Company>ELY</Company>
<Role>PR00015003_ELY_Laadunvalvoja</Role>
</member>
<member>
<AccountName>LX444316</AccountName>
<FirstName>Pilvi</FirstName>
<LastName>Hyvönen</LastName>
<DisplayName>Hyvönen Pilvi YIT Rakennus Oy</DisplayName>
<Email>pilvi.hyvonen@yit.fi</Email>
<MobilePhone>0503831293</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LXKARHUJA</AccountName>
<FirstName>Jaakko</FirstName>
<LastName>Karhu</LastName>
<DisplayName>Karhu Jaakko YIT Rakennus Oy</DisplayName>
<Email>jaakko.karhu@yit.fi</Email>
<MobilePhone>0504402557</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LX147762</AccountName>
<FirstName>Pekka</FirstName>
<LastName>Lääkkö</LastName>
<DisplayName>Lääkkö Pekka YIT Rakennus Oy</DisplayName>
<Email>pekka.laakko@yit.fi</Email>
<MobilePhone>0503828213</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LXNISSIIL</AccountName>
<FirstName>Ilkka</FirstName>
<LastName>Nissilä</LastName>
<DisplayName>Nissilä Ilkka YIT Rakennus Oy</DisplayName>
<Email>Ilkka.nissila@yit.fi</Email>
<MobilePhone>0403570833</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LX152636</AccountName>
<FirstName>Panu</FirstName>
<LastName>Palvelukeskus</LastName>
<DisplayName>Palvelukeskus Panu YIT Rakennus Oy</DisplayName>
<Email>palvelukeskus.panu@gmail.com</Email>
<MobilePhone>0505393879</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LX844229</AccountName>
<FirstName>Henna</FirstName>
<LastName>Penttilä</LastName>
<DisplayName>Penttilä Henna YIT Rakennus Oy</DisplayName>
<Email>henna.penttila@yit.fi</Email>
<MobilePhone>0505600569</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LXSAUREJA</AccountName>
<FirstName>Jarno</FirstName>
<LastName>Saurento</LastName>
<DisplayName>Saurento Jarno YIT Rakennus Oy</DisplayName>
<Email>jarno.saurento@yit.fi</Email>
<MobilePhone>0503902669</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
<member>
<AccountName>LXYITPA</AccountName>
<FirstName>Panu</FirstName>
<LastName>Yit</LastName>
<DisplayName>Yit Panu YIT Rakennus Oy</DisplayName>
<Email>yr.kelipalvelu@yit.fi</Email>
<MobilePhone>0503906852</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>PR00015003_vastuuhenkilo</Role>
</member>
</Members>")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    livi-jarjestelmakayttaja
    :fim (component/using
           (fim/->FIM fim-url)
           [:db :integraatioloki])
    :api-yhteystiedot (component/using
                        (api-yhteystiedot/->Yhteystiedot)
                        [:http-palvelin :db :integraatioloki :fim])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (with-fake-http
    [(str "http://localhost:" portti "/api/urakat/yhteystiedot/1238") :allow
     fim-url fim-vastaus]
    (let [vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/1238" livi-jarjestelmakayttaja portti)]
      (log/debug "Vastaus saatiin: " (pr-str vastaus))
      (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla"))))


