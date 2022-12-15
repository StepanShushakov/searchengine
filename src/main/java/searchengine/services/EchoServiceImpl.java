

package searchengine.services;

import org.springframework.stereotype.Service;

@Service
public class EchoServiceImpl implements EchoService {
    @Override
    public boolean echo() {
        return true;
    }
}
